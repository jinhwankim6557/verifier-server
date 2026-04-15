# OID4VP Admin 설정 항목 — SDK 사용처 및 표준 스펙 근거 분석

> 작성일: 2026-03-23
> 목적: Mockup/Admin에서 관리하는 각 설정 항목이 SDK 내부에서 실제로 어디서 사용되는지, OID4VP 표준 스펙(Draft 23)에 근거하는지를 정리

---

## 1. OID4VP Config

SDK Object: `OID4VPConfig.java`
DB 테이블: `oid4vp_config` (config 컬럼에 JSON 전체 저장)

| Config 필드 | SDK 사용 위치 | 호출 시점 | 용도 |
|-------------|-------------|---------|------|
| `baseUrl` | `InitiationService:206,253` | Initiation | request_uri, response에 포함 |
| `clientName` | `ClientMetadataService:39` | Initiation | `client_metadata.client_name` 생성 |
| `invocationScheme` | `InitiationService:204,224` | Initiation | Authorization Request URL 프리픽스 (`openid4vp://`) |
| `clientId.scheme` | `OID4VPConfig.buildClientId():92` | Initiation + VP검증 | `scheme:value` 조합으로 client_id 생성 |
| `clientId.value` | `OID4VPConfig.buildClientId():92` | Initiation + VP검증 | 위와 동일 |
| `session.sessionTtl` | `AuthorizationService:394`, `OID4VPHelperService:453` | 세션 관리 | 세션 만료 판단 |
| `endpoints.response` | `OID4VPConfig.getResponseUrl():80` | Initiation + AuthRequest | `response_uri` / `redirect_uri` 값 |
| `endpoints.request` | `OID4VPConfig.getRequestUrl():83` | Initiation | `request_uri` 생성 |
| `clientMetadata.vpFormatsSupported` | `ClientMetadataService:48` | Initiation + AuthRequest | `client_metadata.vp_formats_supported` 생성 |
| `crypto.vpTokenEncryptionKey` | `VPTokenEncryptor:55` | VP Token 저장 | VP Token 암호화 키 |

**판정: 모든 필드가 실제 SDK 코드에서 사용됨. Admin 설정 항목으로 적절함.**

---

## 2. DCQL Scope Mapping

SDK Object: `DCQLQuery.java` (내부 클래스: `CredentialQuery`, `ClaimQuery`, `ClaimSet`, `CredentialSet`)
DB 테이블: `dcql_scope_mapping` (dcql_query 컬럼에 JSON 저장)

### 2-1. Top-level (`DCQLQuery`)

| 필드 | 스펙 | SDK 사용 위치 | 실제 사용 | Admin 필요 |
|------|------|-------------|----------|-----------|
| `credentials` (배열) | REQUIRED | `InitiationService:88` — scope→DCQL 변환 후 AuthRequest에 포함, `OID4VPHelperService:507-510` — VP 검증 시 기대값 | **O** | **O** |
| `credential_sets` | OPTIONAL | `DCQLQueryValidator:300` — 유효성 검증만 수행, 실제 처리 로직 없음 | **X** (검증만) | 현재 불필요 |
| `transaction_data` | OPTIONAL | 사용처 없음 | **X** (dead field) | 불필요 |

### 2-2. CredentialQuery

| 필드 | 스펙 | SDK 사용 위치 | 실제 사용 | Admin 필요 |
|------|------|-------------|----------|-----------|
| `id` | REQUIRED | `OID4VPHelperService:538` — VP Token의 credential ID와 매칭 검증 | **O** | **O** |
| `format` | REQUIRED | `OID4VPHelperService:544` — VP Token의 format과 비교 검증 | **O** | **O** |
| `meta` | OPTIONAL | `OID4VPHelperService:551-556` — `DCQLCredentialMatcher.matchesMetadata()` 호출 | **O** | **O** |
| `claims` | OPTIONAL | AuthRequest JSON에 포함되어 Wallet에 전달 (Wallet이 어떤 claim을 줄지 결정) | **O** (Wallet 전달) | **O** |
| `claim_sets` | OPTIONAL | `DCQLQueryValidator` 유효성만 검증, 처리 로직 없음 | **X** (검증만) | 현재 불필요 |
| `purpose` | 비표준 (SDK 확장) | AuthRequest JSON에 포함 | **O** (Wallet 표시용) | 있으면 좋음 |
| `require_cryptographic_holder_binding` | 스펙 존재 | SDK에서 사용처 미확인 | **미확인** | 현재 불필요 |

### 2-3. ClaimQuery

| 필드 | 스펙 | SDK 사용 위치 | 실제 사용 | Admin 필요 |
|------|------|-------------|----------|-----------|
| `id` | OPTIONAL | AuthRequest JSON에 포함 (Wallet 전달) | **O** (Wallet 전달) | 있으면 좋음 |
| `path` | REQUIRED | AuthRequest JSON에 포함 (Wallet이 이 경로의 claim을 제출) | **O** (Wallet 전달) | **O** |
| `values` | OPTIONAL | `DCQLCredentialMatcher:268` 에 코드 있으나 **`extractMatchingClaimNames()` 자체가 어디서도 호출되지 않음** | **X** (dead code) | 스펙상 유효하나 서버 미사용 |
| `value` | **비표준** | `DCQLCredentialMatcher:276` — dead code | **X** | 불필요 |
| `min` | **비표준** | `DCQLCredentialMatcher:284` — dead code | **X** | 불필요 |
| `max` | **비표준** | `DCQLCredentialMatcher:290` — dead code | **X** | 불필요 |
| `purpose` | 비표준 (SDK 확장) | AuthRequest JSON에 포함 | **O** (Wallet 표시용) | 있으면 좋음 |

### 2-4. Meta 키 (format별)

| Format | Meta 키 | 스펙 근거 | SDK 사용 위치 | 실제 사용 |
|--------|---------|---------|-------------|----------|
| `dc+sd-jwt` | `vct_values` | OID4VP Appendix B.3.5 | `SDJWTCredentialAdapter:105-107` — VP 검증 시 vct 매칭 | **O** |
| `vc+sd-jwt` | `vct_values` | OID4VP Appendix B.3.5 | `SDJWTCredentialAdapter:105-107` — 동일 | **O** |
| `opendid_vc` | `credential_schema_id_values` | **비표준** (OpenDID 자체) | `OpenDIDVCCredentialAdapter:112-114` — schema ID 매칭 | **O** (내부용) |

### 2-5. Format 값

| Format | OID4VP 스펙 | SDK Adapter | Mockup | 비고 |
|--------|-----------|-------------|--------|------|
| `dc+sd-jwt` | O (Draft 23) | `SDJWTCredentialAdapter` | O | 표준 |
| `vc+sd-jwt` | O | `SDJWTCredentialAdapter` | O | 표준 |
| `opendid_vc` | **X** (비표준) | `OpenDIDVCCredentialAdapter` | O | OpenDID 자체 포맷 |
| `mso_mdoc` | O | Adapter 없음 | X | SDK 미지원 |
| `jwt_vc_json` | O | Adapter 없음 (Validator에서 known으로만 등록) | X | SDK 미지원 |

---

## 3. SDK 내부 흐름에서의 사용 경로

### 3-1. Initiation 단계 (Admin 설정 → Authorization Request 생성)

```
Policy(scope)
  → ScopeToDCQLMapperService.scopeToDCQL(scope)
      → DB에서 dcql_scope_mapping 조회
      → DCQLQuery 객체 반환
  → DCQLQueryValidator.validate(dcqlQuery)
      → format, meta 구조 유효성 검증
  → InitiationService.buildDirectResponse()
      → config.buildClientId()              ← OID4VP Config
      → config.getInvocationScheme()         ← OID4VP Config
      → config.getResponseUrl()              ← OID4VP Config
      → dcql_query JSON                      ← DCQL Scope Mapping
      → client_metadata JSON                 ← OID4VP Config (clientName, vpFormatsSupported)
  → Authorization Request URL 생성 완료
```

### 3-2. VP Token 검증 단계 (Wallet 응답 수신 후)

```
OID4VPHelperService.processVpTokenWithDCQL()
  → 세션에서 저장된 dcqlQuery 로드
  → for (각 credential in vpToken):
      1. ID 매칭:     credentialType == credentials[i].id
      2. Format 매칭: verifier.getFormat() == credentials[i].format
      3. Meta 매칭:   DCQLCredentialMatcher.parseCredential() → matchesMetadata()
                        → SDJWTAdapter: vct_values 목록에 포함?
                        → OpenDIDAdapter: credential_schema_id 일치?
      4. Binding:     verifier.validatePresentationBinding(clientId, nonce)
      5. 서명:        verifier.validateSignature() 또는 validateSignatureWithX5c()
  → credential 개수 == DCQL에서 요청한 개수 확인
  → 세션 상태 COMPLETED
```

### 3-3. 호출되지 않는 코드 (Dead Code)

```
DCQLCredentialMatcher 내부:
  ❌ matchesFormat()              — 어디서도 호출 안됨
  ❌ extractMatchingClaimNames()  — 어디서도 호출 안됨
      └─ processPathAndCollectClaims()
          └─ collectMatchingValuesFromPath()
              └─ meetsClaimConditions()
                  ├─ values 비교    ← 스펙에 있으나 서버에서 미사용
                  ├─ value 비교     ← 비표준
                  ├─ checkMinCondition() ← 비표준
                  └─ checkMaxCondition() ← 비표준
```

---

## 4. 결론: Mockup/Admin 입력 항목 적정성

### 유지해야 할 항목

| 항목 | 근거 |
|------|------|
| OID4VP Config 전체 (10개 필드) | SDK에서 모든 필드 사용 |
| CredentialQuery: `id`, `format`, `meta` | VP Token 검증에 직접 사용 |
| ClaimQuery: `path` | Wallet에 전달하는 핵심 필드 |
| Format: `dc+sd-jwt`, `vc+sd-jwt`, `opendid_vc` | SDK Adapter 존재 |
| Meta: `vct_values` (SD-JWT), `credential_schema_id_values` (OpenDID) | VP 검증 시 매칭에 사용 |

### 있으면 좋지만 필수 아닌 항목

| 항목 | 근거 |
|------|------|
| ClaimQuery: `id` | Wallet 표시용, 서버 검증 안함 |
| ClaimQuery: `purpose` | Wallet 표시용 |
| CredentialQuery: `purpose` | Wallet 표시용 |
| ClaimQuery: `values` | 스펙 유효, AuthRequest에 포함되나 서버 검증 dead code |

### 불필요한 항목 (Admin에서 제외 가능)

| 항목 | 근거 |
|------|------|
| `credential_sets` | SDK에서 유효성 검증만, 처리 로직 없음 |
| `transaction_data` | SDK 사용처 없음 |
| `claim_sets` | SDK에서 유효성 검증만 |
| ClaimQuery: `value`, `min`, `max` | 비표준 + dead code |
| `require_cryptographic_holder_binding` | SDK 사용처 미확인 |
