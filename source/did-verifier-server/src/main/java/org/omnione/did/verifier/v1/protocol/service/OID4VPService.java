package org.omnione.did.verifier.v1.protocol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.datamodel.enums.ProofPurpose;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.domain.VerifierInfo;
import org.omnione.did.base.db.repository.Oid4vpSessionJpaRepository;
import org.omnione.did.base.db.repository.Oid4vpSessionMappingRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.util.BaseCoreDidUtil;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.did.VerificationMethod;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.service.AuthorizationService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.MultibaseUtils;
import org.omnione.did.oid4vc.oid4vp.util.jar.jws.CompactSigner;
import org.omnione.did.verifier.v1.admin.service.VerifierInfoQueryService;
import org.omnione.did.verifier.v1.agent.service.DidDocService;
import org.omnione.did.verifier.v1.agent.service.FileWalletService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OID4VPService {

    private final AuthorizationService authorizationService;
    private final Oid4vpSessionMappingRepository sessionMappingRepository;
    private final Oid4vpSessionJpaRepository oid4vpSessionJpaRepository;
    private final TransactionService transactionService;
    private final FileWalletService fileWalletService;
    private final VerifierInfoQueryService verifierInfoQueryService;
    private final DidDocService didDocService;
    private final ObjectMapper objectMapper;

    /**
     * Authorization Request JWT 조회 (Wallet이 request_uri로 호출)
     * FileWallet의 assert 키로 JWT 서명
     */
    public ServiceResult<String> getAuthorizationRequest(String requestId) {
        log.debug("=== OID4VP getAuthorizationRequest for requestId: {} ===", requestId);

        Oid4vpSessionMapping mapping = sessionMappingRepository.findByOid4vpRequestId(requestId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());
        validateTransaction(transaction);

        VerifierInfo verifierInfo = verifierInfoQueryService.getVerifierInfo();
        DidDocument verifierDidDoc = didDocService.getDidDocument(verifierInfo.getDid());

        String keyId = ProofPurpose.ASSERTION_METHOD.toKeyId();
        VerificationMethod vm = BaseCoreDidUtil.getVerificationMethod(verifierDidDoc, keyId);
        String verificationMethod = verifierDidDoc.getId() + "?versionId=" + verifierDidDoc.getVersionId() + "#" + vm.getId();
        String publicKeyMultibase = vm.getPublicKeyMultibase();

        CompactSigner compactSigner = (kid, hash) -> fileWalletService.generateCompactSignature(kid, hash);

        ServiceResult<String> result = authorizationService.getAuthorizationRequest(
                requestId, compactSigner, verificationMethod, publicKeyMultibase);

        if (!result.isSuccess()) {
            log.error("Failed to get authorization request: {} - {}", result.getErrorCode(), result.getErrorDescription());
            throw new OpenDidException(ErrorCode.OID4VP_AUTHORIZATION_REQUEST_FAILED);
        }

        log.debug("*** Authorization request retrieved for requestId: {} ***", requestId);
        return result;
    }

    /**
     * VP Token 응답 처리 (Wallet이 vp_token 제출)
     * DCQL 쿼리의 credential id를 세션에서 읽어 vpTokenMap 키로 동적 매핑
     */
    @Transactional
    public Oid4vpResponseResult receiveResponse(Oid4vpResponseRequest request) {
        log.debug("=== OID4VP receiveResponse for state: {} ===", request.getState());

        // 1. state로 세션 매핑 조회
        Oid4vpSessionMapping mapping = sessionMappingRepository.findByState(request.getState())
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        // 2. Transaction 유효성 확인
        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());
        validateTransaction(transaction);

        // 3. oid4vp_session에서 DCQL 쿼리 조회 → credential ID 동적 추출
        Oid4vpSession oid4vpSession = oid4vpSessionJpaRepository.findByState(request.getState())
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        Map<String, List<Object>> vpTokenMap = buildVpTokenMap(oid4vpSession.getDcqlQuery(), request.getVpToken());
        log.debug("vpTokenMap keys: {}", vpTokenMap.keySet());

        // 4. VP Token에서 issuer/holder DID를 추출하여 공개키 해석
        // - DID Document의 publicKeyMultibase → multibase 디코딩 → Base64(압축키)
        // - OpenDIDVPVerifier.validateSignature()가 기대하는 포맷: Base64(secp256r1 압축키 33bytes)
        // - oid4vp.dev.skip-signature-verification=true 설정 시 실제 검증은 건너뜀
        List<String> issuerPublicKeys = resolveIssuerPublicKeys(vpTokenMap);
        List<String> holderPublicKeys = resolveHolderPublicKeys(vpTokenMap);
        log.debug("Resolved issuerPublicKeys: {}, holderPublicKeys: {}",
                issuerPublicKeys.size(), holderPublicKeys.size());

        ServiceResult<Map<String, Object>> result = authorizationService.receiveResponse(
                vpTokenMap,
                issuerPublicKeys.isEmpty() ? List.of("unresolved-issuer-key") : issuerPublicKeys,
                holderPublicKeys.isEmpty() ? null : holderPublicKeys,
                request.getState(),
                request.getError(),
                request.getErrorDescription(),
                "POST"
        );

        // 5. Transaction 상태 업데이트
        if (result.isSuccess()) {
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.COMPLETED);
            log.debug("*** OID4VP response processed. txId={}, status=COMPLETED ***", mapping.getTxId());

            return Oid4vpResponseResult.builder()
                    .sessionId(mapping.getTxId())
                    .status("COMPLETED")
                    .build();
        } else {
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            log.error("OID4VP response failed: {} - {}", result.getErrorCode(), result.getErrorDescription());

            return Oid4vpResponseResult.builder()
                    .sessionId(mapping.getTxId())
                    .status("FAILED")
                    .error(result.getErrorCode())
                    .errorDescription(result.getErrorDescription())
                    .build();
        }
    }

    /**
     * DCQL 쿼리의 credentials[].id를 파싱하여 vpTokenMap 구성
     * - 단일 credential: 해당 id를 키로 vpToken 매핑
     * - 복수 credential: 모든 id에 동일한 vpToken 매핑 (단일 토큰 제출 시)
     * - 파싱 실패 시: "default" fallback
     */
    private Map<String, List<Object>> buildVpTokenMap(String dcqlQueryJson, String vpToken) {
        Map<String, List<Object>> vpTokenMap = new LinkedHashMap<>();
        if (vpToken == null) {
            return vpTokenMap;
        }

        if (dcqlQueryJson != null) {
            try {
                JsonNode dcql = objectMapper.readTree(dcqlQueryJson);
                JsonNode credentials = dcql.get("credentials");
                if (credentials != null && credentials.isArray() && !credentials.isEmpty()) {
                    for (JsonNode cred : credentials) {
                        JsonNode idNode = cred.get("id");
                        if (idNode != null && !idNode.isNull()) {
                            vpTokenMap.put(idNode.asText(), List.of(vpToken));
                        }
                    }
                    if (!vpTokenMap.isEmpty()) {
                        return vpTokenMap;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse DCQL query, using 'default' fallback: {}", e.getMessage());
            }
        }

        // fallback
        vpTokenMap.put("default", List.of(vpToken));
        return vpTokenMap;
    }

    /**
     * vpTokenMap의 각 credential을 순회하며 포맷별 resolver로 issuer 공개키를 해석한다.
     * 반환 순서는 SDK의 keyIndex 기반 매핑(credential flat 순서)에 대응한다.
     *
     * 지원 포맷:
     *   - JSON_VP   (OpenDID / W3C VC JSON-LD): verifiableCredential[].proof.verificationMethod
     *   - SD_JWT    (IETF SD-JWT VC): issuer JWS 헤더의 kid
     *   - MDOC      (ISO/IEC 18013-5): 향후 추가 예정
     */
    private List<String> resolveIssuerPublicKeys(Map<String, List<Object>> vpTokenMap) {
        List<String> keys = new ArrayList<>();
        if (vpTokenMap == null || vpTokenMap.isEmpty()) return keys;
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                VpTokenFormat format = detectFormat(credential);
                switch (format) {
                    case JSON_VP -> keys.addAll(resolveIssuerKeysFromJsonVp(credential));
                    case SD_JWT -> resolveIssuerKeyFromSdJwt((String) credential).ifPresent(keys::add);
                    case MDOC -> log.warn("mDoc issuer key resolution not yet implemented");
                    case UNKNOWN -> log.warn("Unknown VP token format; skipping issuer key resolution");
                }
            }
        }
        return keys;
    }

    /**
     * vpTokenMap의 각 credential을 순회하며 포맷별 resolver로 holder 공개키를 해석한다.
     * holder key가 없는 credential(예: key binding 없는 SD-JWT)은 건너뛴다.
     */
    private List<String> resolveHolderPublicKeys(Map<String, List<Object>> vpTokenMap) {
        List<String> keys = new ArrayList<>();
        if (vpTokenMap == null || vpTokenMap.isEmpty()) return keys;
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                VpTokenFormat format = detectFormat(credential);
                switch (format) {
                    case JSON_VP -> resolveHolderKeyFromJsonVp(credential).ifPresent(keys::add);
                    case SD_JWT -> resolveHolderKeyFromSdJwt((String) credential).ifPresent(keys::add);
                    case MDOC -> log.warn("mDoc holder key resolution not yet implemented");
                    case UNKNOWN -> log.warn("Unknown VP token format; skipping holder key resolution");
                }
            }
        }
        return keys;
    }

    /**
     * credential 형태로 포맷을 감지한다.
     *   - String이 '{'로 시작: JSON VP (직렬화 문자열)
     *   - String에 '~' 포함: SD-JWT (compact format with disclosures)
     *   - Map: JSON VP (파싱된 객체)
     * mDoc은 base64url CBOR 문자열이라 별도 구분자가 없어 향후 heuristic 추가 필요.
     */
    private VpTokenFormat detectFormat(Object credential) {
        if (credential instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{")) return VpTokenFormat.JSON_VP;
            if (t.contains("~")) return VpTokenFormat.SD_JWT;
            return VpTokenFormat.UNKNOWN;
        }
        if (credential instanceof Map) return VpTokenFormat.JSON_VP;
        return VpTokenFormat.UNKNOWN;
    }

    private List<String> resolveIssuerKeysFromJsonVp(Object credential) {
        List<String> keys = new ArrayList<>();
        try {
            JsonNode vp = toJsonNode(credential);
            JsonNode vcs = vp.get("verifiableCredential");
            if (vcs == null || !vcs.isArray()) return keys;
            for (JsonNode vc : vcs) {
                JsonNode proof = vc.get("proof");
                if (proof == null) continue;
                JsonNode vmNode = proof.get("verificationMethod");
                if (vmNode == null) continue;
                resolveKeyByVerificationMethod(vmNode.asText()).ifPresent(keys::add);
            }
        } catch (Exception e) {
            log.warn("Failed to parse JSON VP for issuer key resolution: {}", e.getMessage());
        }
        return keys;
    }

    private Optional<String> resolveHolderKeyFromJsonVp(Object credential) {
        try {
            JsonNode vp = toJsonNode(credential);
            JsonNode proof = vp.get("proof");
            if (proof == null) return Optional.empty();
            JsonNode vmNode = proof.get("verificationMethod");
            if (vmNode == null) return Optional.empty();
            return resolveKeyByVerificationMethod(vmNode.asText());
        } catch (Exception e) {
            log.warn("Failed to parse JSON VP for holder key resolution: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * SD-JWT issuer JWS 헤더의 kid(DID URL)로 issuer 공개키를 해석한다.
     * 형식: issuerJwt ~ disclosure1 ~ ... ~ kbJwt
     */
    private Optional<String> resolveIssuerKeyFromSdJwt(String sdJwt) {
        try {
            String issuerJwt = sdJwt.split("~", 2)[0];
            String[] jwtParts = issuerJwt.split("\\.");
            if (jwtParts.length < 2) return Optional.empty();
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(jwtParts[0]));
            JsonNode kidNode = header.get("kid");
            if (kidNode == null || kidNode.isNull()) {
                log.warn("SD-JWT header has no 'kid'; cannot resolve issuer public key");
                return Optional.empty();
            }
            return resolveKeyByVerificationMethod(kidNode.asText());
        } catch (Exception e) {
            log.warn("Failed to resolve issuer public key from SD-JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * SD-JWT issuer payload의 cnf.kid(DID URL)로 holder 공개키를 해석한다.
     * cnf.jwk/x5c 형태는 현재 DID 기반 해석이 아니므로 null 반환.
     */
    private Optional<String> resolveHolderKeyFromSdJwt(String sdJwt) {
        try {
            String issuerJwt = sdJwt.split("~", 2)[0];
            String[] jwtParts = issuerJwt.split("\\.");
            if (jwtParts.length < 2) return Optional.empty();
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(jwtParts[1]));
            JsonNode cnf = payload.get("cnf");
            if (cnf == null || cnf.isNull()) return Optional.empty();
            JsonNode kidNode = cnf.get("kid");
            if (kidNode == null || kidNode.isNull()) {
                log.debug("SD-JWT cnf has no 'kid'; holder key resolution skipped");
                return Optional.empty();
            }
            return resolveKeyByVerificationMethod(kidNode.asText());
        } catch (Exception e) {
            log.warn("Failed to resolve holder public key from SD-JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> resolveKeyByVerificationMethod(String verificationMethod) {
        String did = extractDid(verificationMethod);
        String keyId = extractKeyId(verificationMethod);
        try {
            DidDocument didDoc = didDocService.getDidDocument(did);
            VerificationMethod vm = BaseCoreDidUtil.getVerificationMethod(didDoc, keyId);
            String base64Key = multibaseToBase64(vm.getPublicKeyMultibase());
            log.debug("Resolved public key for DID: {}, keyId: {}", did, keyId);
            return Optional.of(base64Key);
        } catch (Exception e) {
            log.warn("Failed to resolve public key [{}#{}]: {}", did, keyId, e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode toJsonNode(Object credential) throws java.io.IOException {
        if (credential instanceof String s) return objectMapper.readTree(s);
        return objectMapper.valueToTree(credential);
    }

    /** VP Token credential 포맷 식별자. mDoc 등 확장 포맷을 이곳에 추가한다. */
    private enum VpTokenFormat {
        JSON_VP,
        SD_JWT,
        MDOC,
        UNKNOWN
    }

    /**
     * verificationMethod에서 DID 부분을 추출한다.
     * "did:omn:holder?versionId=1#auth" → "did:omn:holder"
     */
    private String extractDid(String verificationMethod) {
        int queryIdx = verificationMethod.indexOf('?');
        int hashIdx = verificationMethod.indexOf('#');
        if (queryIdx > 0) return verificationMethod.substring(0, queryIdx);
        if (hashIdx > 0) return verificationMethod.substring(0, hashIdx);
        return verificationMethod;
    }

    /**
     * verificationMethod에서 keyId 부분을 추출한다.
     * "did:omn:holder?versionId=1#auth" → "auth"
     */
    private String extractKeyId(String verificationMethod) {
        int hashIdx = verificationMethod.indexOf('#');
        if (hashIdx >= 0) return verificationMethod.substring(hashIdx + 1);
        return "";
    }

    /**
     * DID Document의 publicKeyMultibase를 OpenDIDVPVerifier.validateSignature()가 기대하는
     * Base64(secp256r1 압축키 33bytes) 형태로 변환한다.
     *
     * multibase 지원 prefix:
     *   'm' → base64 standard
     *   'u' → base64url
     *   'z' → base58btc (MultibaseUtils 사용)
     */
    private String multibaseToBase64(String publicKeyMultibase) throws Exception {
        byte[] keyBytes = MultibaseUtils.decodeMultibase(publicKeyMultibase);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    private void validateTransaction(Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new OpenDidException(ErrorCode.TRANSACTION_INVALID);
        }
        if (transaction.getExpired_at().isBefore(java.time.Instant.now())) {
            throw new OpenDidException(ErrorCode.TRANSACTION_EXPIRED);
        }
    }
}
