# SD-JWT 서명 검증 실패 분석 보고서 (최종)

> 대상 credential: **NationalID** (`vct = urn:eudi:pid:1`, EUDI PID), 포맷 `dc+sd-jwt`
> 증상 로그: `org.omnione.did.sdjwt.exception.SDJWTException: Credential JWT signature verification failed`

---

## 0. 한 줄 결론

**검증(Verifier)·키 등록(DID Document)·전송에는 문제가 없습니다. 서명에 쓴 키도 진짜 `#assert`가 맞습니다.**
유일한 원인은 **발급기가 issuer 서명을 만들 때 `header.payload`를 SHA‑256으로 *두 번*(double‑SHA256) 해시한 뒤 서명**한다는 것입니다.
표준 ES256 검증기는 SHA‑256을 *한 번*만 해시하므로 해시값이 달라 영영 검증되지 않습니다.

> **고칠 곳은 발급기의 해시 1회뿐입니다.** (직접 ECDSA 검증으로 증명 — 3장)

---

## 1. 증상 요약

| 항목 | 내용 |
|---|---|
| 실패 credential | `NationalID` (SD-JWT, `urn:eudi:pid:1`) |
| 성공 credential(비교군) | `driver_license` (OpenDID VC) — 정상 통과 |
| 실패 지점 로그 | `Credential JWT signature verification failed` |
| 서명 키 | **`did:omn:issuer#assert` 가 맞음** (발급팀 주장 옳음) |
| 서명원문(message) | **`header.payload` 가 맞음** |
| 진짜 원인 | **해시를 1회가 아니라 2회(SHA256d) 적용** |
| `holderPublicKey: null` | 원인 아님 (부록 참고) |

---

## 2. SD-JWT는 어떻게 검증되나 (쉽게) + 핵심 관찰

SD-JWT 한 장은 `~`로 이어진 부분들로 되어 있고, **서명이 두 개**입니다.

```
<issuer JWT> ~ <disclosure 1> ~ … ~ <disclosure N> ~ <KB-JWT>
   └ 발급자 서명 ┘                                   └ 소유자(holder) 서명 ┘
```

1. **issuer 서명** — "진짜 발급기관이 만들었다" → 발급기관 공개키(`#assert`)로 검증
2. **holder 서명(KB-JWT)** — "지금 제출하는 사람이 소유자다" → 소유자 공개키(`cnf`)로 검증

서명 = ECDSA. ECDSA는 "메시지를 **해시**한 값"에 서명합니다. ES256 표준은 **SHA-256을 한 번**입니다.

> **결정적 관찰:** 이 크레덴셜 한 장 안에서 **holder(KB-JWT)는 1중 해시(표준, 정상)**, **issuer는 2중 해시(비표준, 실패)** 로 서명돼 있습니다. 같은 문서 안에서 두 서명의 해시 방식이 다릅니다 → 문제는 **발급기 issuer 서명 한 곳**에 국한됩니다.

---

## 3. 증명 — 직접 ECDSA 검증 결과

`#assert` 공개키(`publicKeyMultibase: z2BJ5iwpnDrCWQW7yeuA1k2mHQQh4fmTbpwJ5ywRmxYrhB`)를 디코드해, 서로 다른 두 시점에 발급된 credential 두 장으로 표준 ECDSA(P-256) 검증했습니다.

| 검증 대상 | 키 | 1중 해시(표준 ES256) | 2중 해시(SHA256d) |
|---|---|---|---|
| **issuer 서명 (cred1)** | `#assert` | ❌ INVALID | ✅ **VALID** |
| **issuer 서명 (cred2)** | `#assert` | ❌ INVALID | ✅ **VALID** |
| **KB-JWT 서명 (양성대조)** | `cnf`(holder) | ✅ **VALID** | — |

근거 3가지:

1. **양성대조** — KB-JWT(홀더 서명)를 같은 코드로 검증하면 **1중 해시로 정확히 통과** → 우리 검증 코드가 정확하고, 표준이 1중임을 확인.
2. **2샘플 교차** — issuer 서명을 1중 해시로 역산하면 두 장의 키가 안 겹침(허상). **2중 해시로 바꾸자 두 장이 `#assert` 하나로 정확히 수렴** → 발급기가 2중 해시 + #assert 키로 서명함이 확정.
3. **데이터 무결성** — KB-JWT의 `sd_hash`가 제출 본문과 일치 → 전송 중 변조 없음.

---

## 4. 우리(검증) 쪽을 바꿔야 하나? → 아니오

| 우리 검증 해시 | 이 발급기 issuer | KB-JWT(홀더) | driver_license / 표준 발급기 |
|---|---|---|---|
| **1중(현재, 표준)** | ❌ | ✅ | ✅ |
| 2중으로 변경 | ✅ | ❌ | ❌ |

같은 크레덴셜이 **내부적으로 불일치**(발급기만 2중)이므로, **우리 해시를 어느 쪽으로 정해도 둘 다 통과시키는 값은 없습니다.** 우리가 2중으로 바꾸면 홀더 서명·OpenDID VC·전 세계 표준 SD-JWT가 전부 깨지고 보안/상호운용성만 망가집니다. **수정은 발급기 한 곳에서만 가능합니다.**

---

## 5. 발급 측 원인과 수정

발급 로그가 정확히 그 지점을 보여줍니다:

```
FileWalletService : Compact signature generated for keyId: assert
signature length : 65
Detected 65-byte signature with prefix 0x1F - extracting last 64 bytes
```

- 표준 ES256 서명은 64바이트(r‖s)인데, `FileWalletService`는 **65바이트 + `0x1F` 프리픽스**를 내놓습니다. `0x1F`는 **비트코인/secp256k1 계열 recoverable 서명**의 헤더 바이트로, 이 서명 루틴이 **블록체인식(이중 SHA-256)** 임을 시사합니다.
- 추정 메커니즘: `header.payload`에 SHA-256을 걸어 32바이트 다이제스트를 만든 뒤, 이를 다시 `SHA256withECDSA`(또는 비트코인식 SHA256d) 서명기에 넘겨 **한 번 더 해시** → 결과적으로 `SHA256(SHA256(header.payload))`에 서명.

**수정 방법 (셋 중 하나):**
1. SD-JWT issuer 서명을 **표준 `SHA256withECDSA`로 `header.payload` 원문에 직접** 적용(해시 1회). ← 정석
2. 지갑이 "이미 해시된 32바이트"를 받는 구조라면, **`NONEwithECDSA`**(다이제스트 직접 서명)로 바꿔 순효과를 1중으로.
3. 블록체인용 compact/recoverable 서명 루틴(이중해시·`0x1F`)을 SD-JWT 경로에서 **재사용 금지.**

**확정 셀프체크:** 64바이트 서명 직후, `#assert` 공개키로 `header.payload`를 표준 `SHA256withECDSA` 검증 → 지금은 false, 해시를 1회로 고치면 → true. 이 토글 하나로 끝.

**driver_license가 되는 이유:** 그건 이중해시를 안 하는 다른(정상) 서명 경로를 타기 때문입니다. SD-JWT 발급 경로(`SdJwtGeneratorWithDID` + `FileWalletService`)에만 이 이중해시가 들어 있습니다.

---

## 6. 발급팀이 직접 재현하는 법 (jwt.io)

1. credential에서 **첫 `~` 앞부분**(issuer JWT)만 복사
2. https://jwt.io → Algorithm **ES256** → 아래 `#assert` 공개키 입력
3. → **"Invalid signature"** 표시됨. (jwt.io 포함 모든 표준 검증기는 1중 해시 → 다 실패) = 우리만의 문제가 아님을 증명.

```
#assert 공개키 (did:omn:issuer#assert, Secp256r1/P-256)
  multibase  : z2BJ5iwpnDrCWQW7yeuA1k2mHQQh4fmTbpwJ5ywRmxYrhB
  compressed : 03f6baecdf0c41b90a2f1918521ebd27902031d13aae21f66486caa626a1a9eec6
  JWK        : {"kty":"EC","crv":"P-256",
                "x":"9rrs3wxBuQovGRhSHr0nkCAx0TquIfZkhsqmJqGp7sY",
                "y":"8gnkuD6Izt1gGSoM4GFl6EDcL1r02a62ghkqvs9qUz0"}
  PEM        :
    -----BEGIN PUBLIC KEY-----
    MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9rrs3wxBuQovGRhSHr0nkCAx0Tqu
    IfZkhsqmJqGp7sbyCeS4PojO3WAZKgzgYWXoQNwvWvTZrraCGSq+z2pTPQ==
    -----END PUBLIC KEY-----
```

---

## 부록 A. 실제 데이터 근거

```
issuer JWT header : {"alg":"ES256","typ":"dc+sd-jwt","kid":"did:omn:issuer?versionId=1#assert"}
issuer JWT payload: {"iss":"did:omn:issuer","vct":"urn:eudi:pid:1","cnf":{"jwk":{holder 키}}, "_sd":[20개]}

#assert 공개키 (DID Doc) : compressed 03f6baec… / x = f6baecdf0c41b90a2f1918521ebd27902031d13aae21f66486caa626a1a9eec6
cnf.jwk 공개키 (holder)  : x = aed2b08c…  ← #assert 와 다른 별개의 홀더 키 (정상)
   ※ cnf.jwk x·y 가 32바이트가 아니라 33바이트(앞 0x00) = 발급기의 비표준 EC 직렬화(별개 경미 이슈)

데이터 무결성 : KB-JWT sd_hash(Q5PlVPN8aqb-fR_zahA1ornnfiS_cJz8vL-dx_TUUVo) 와 제출 본문(disclosure 20개) 일치 ✅
검증 결과     : issuer 서명 vs #assert → 1중해시 ❌ INVALID / 2중해시 ✅ VALID  (cred1·cred2 동일)
                KB-JWT  서명 vs cnf     → 1중해시 ✅ VALID  (양성대조)
```

## 부록 B. 우리 검증 흐름 (참고)

| 단계 | 컴포넌트 | 클래스.메서드 |
|---|---|---|
| 진입 | Server | `OID4VPController.receiveResponse` → `OID4VPService.receiveResponse` |
| 키 조회 | Server | `OID4VPService.resolveIssuerKeyFromSdJwt` / `resolveKeyByVerificationMethod` |
| 조건 검증 | oid4vp-sdk | `OID4VPHelperService.processVPTokenWithDCQL` |
| 키 디코딩 | formatter | `SDJWTVPVerifier.validateSignature` / `KeyUtil.unCompressPublicKey` |
| 검증 코어 | sd-jwt-sdk | `SDJWTVerifier.verify` → `verifyJWTSignature` |
| 서명 대조 | sd-jwt-sdk | `ECDSAVerifier.verify` (`SHA256withECDSA`, **해시 1회 = 표준**) |
```
