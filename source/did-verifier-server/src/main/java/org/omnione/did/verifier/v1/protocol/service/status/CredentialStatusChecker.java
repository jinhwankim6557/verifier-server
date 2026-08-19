package org.omnione.did.verifier.v1.protocol.service.status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.MDocVPVerifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialStatusChecker {

    private static final MDocVPVerifier MDOC_VERIFIER = new MDocVPVerifier();

    private final StatusClaimParser parser;
    private final MdocStatusClaimParser mdocParser;
    private final StatusListTokenFetcher fetcher;
    private final StatusListTokenVerifier tokenVerifier;
    private final StatusListBitDecoder decoder;
    private final VerifierProperty verifierProperty;

    public void checkAll(Map<String, List<Object>> vpTokenMap) {
        if (vpTokenMap == null) return;
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                if (!(credential instanceof String value)) continue;
                if (value.contains("~")) {
                    check(parser.parse(value), "SD-JWT");
                } else if (MDOC_VERIFIER.supports(value)) {
                    // mso_mdoc: status 참조가 MSO(CBOR) 안에 있을 뿐, 조회·판정 절차는 SD-JWT와 동일하다.
                    check(mdocParser.parse(value), "mso_mdoc");
                }
            }
        }
    }

    private void check(Optional<StatusListRef> refOpt, String format) {
        if (refOpt.isEmpty()) {
            log.debug("No status claim in {}, skipping status check", format);
            return;
        }
        StatusListRef ref = refOpt.get();
        log.debug("Checking credential status: idx={}, uri={}", ref.idx(), ref.uri());

        String tokenJwt;
        try {
            tokenJwt = fetcher.fetch(ref.uri());
        } catch (RuntimeException e) {
            if (verifierProperty.getStatusList().isFailOnFetchError()) {
                if (e instanceof OpenDidException) throw (OpenDidException) e;
                throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
            }
            log.warn("Failed to fetch status list (FAIL-OPEN): {}", e.getMessage());
            return;
        }

        StatusListTokenPayload tokenPayload = tokenVerifier.verify(tokenJwt, ref.uri());

        try {
            int status = decoder.extract(tokenPayload.lst(), tokenPayload.bits(), ref.idx());
            switch (status) {
                case 0 -> log.debug("Credential status VALID at idx={}", ref.idx());
                case 1 -> {
                    log.warn("Credential INVALID at idx={}, uri={}", ref.idx(), ref.uri());
                    throw new OpenDidException(ErrorCode.STATUS_LIST_CREDENTIAL_INVALID);
                }
                case 2 -> {
                    log.warn("Credential SUSPENDED at idx={}, uri={}", ref.idx(), ref.uri());
                    throw new OpenDidException(ErrorCode.STATUS_LIST_CREDENTIAL_SUSPENDED);
                }
                case 3 -> {
                    // Issuer의 Status List 연동 가이드는 3을 RESERVED로 정의하며 "유효한 Credential로
                    // 처리하지 않음"을 명시한다. IETF 초안의 "application specific" 해석과 달리 이 값은
                    // 항상 거부 대상이다(fail-open 정책 대상 아님).
                    log.warn("Credential RESERVED(3) at idx={}, uri={}", ref.idx(), ref.uri());
                    throw new OpenDidException(ErrorCode.STATUS_LIST_CREDENTIAL_RESERVED);
                }
                default -> {
                    // IETF status list 초안은 0~3만 정의하고 나머지는 예약값이다. 예약값을 "안전하게 통과"로
                    // 처리하면(CRL/OCSP에서도 알려진 안티패턴) 향후 스펙 확장이나 손상된 값으로 폐기 상태가
                    // 은폐될 수 있어, fetch 실패와 동일한 fail-open/fail-closed 정책을 그대로 적용한다.
                    if (verifierProperty.getStatusList().isFailOnFetchError()) {
                        log.warn("Unknown status value {} at idx={}, rejecting (FAIL-CLOSED)", status, ref.idx());
                        throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
                    }
                    log.warn("Unknown status value {} at idx={}, treating as VALID (FAIL-OPEN)", status, ref.idx());
                }
            }
        } catch (OpenDidException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_INDEX_OUT_OF_BOUNDS);
        } catch (Exception e) {
            log.error("Failed to decode status list: {}", e.getMessage());
            throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }
}
