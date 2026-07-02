package org.omnione.did.verifier.v1.protocol.service.status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialStatusChecker {

    private final StatusClaimParser parser;
    private final StatusListTokenFetcher fetcher;
    private final StatusListTokenVerifier tokenVerifier;
    private final StatusListBitDecoder decoder;
    private final VerifierProperty verifierProperty;

    public void checkAll(Map<String, List<Object>> vpTokenMap) {
        if (vpTokenMap == null) return;
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                if (!(credential instanceof String sdJwt)) continue;
                if (!sdJwt.contains("~")) continue; // SD-JWT만 처리
                checkSingle(sdJwt);
            }
        }
    }

    private void checkSingle(String sdJwt) {
        Optional<StatusListRef> refOpt = parser.parse(sdJwt);
        if (refOpt.isEmpty()) {
            log.debug("No status claim in SD-JWT, skipping status check");
            return;
        }
        StatusListRef ref = refOpt.get();
        log.debug("Checking credential status: idx={}, uri={}", ref.idx(), ref.uri());

        String tokenJwt;
        try {
            tokenJwt = fetcher.fetch(ref.uri());
        } catch (OpenDidException e) {
            if (verifierProperty.getStatusList().isFailOnFetchError()) {
                throw e;
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
                default -> log.warn("Unknown status value {} at idx={}, treating as VALID", status, ref.idx());
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
