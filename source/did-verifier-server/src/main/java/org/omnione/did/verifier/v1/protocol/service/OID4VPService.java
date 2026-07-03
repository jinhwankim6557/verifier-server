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
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPException;
import org.omnione.did.oid4vc.oid4vp.service.AuthorizationService;
import org.omnione.did.oid4vc.oid4vp.service.OID4VPHelperService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.MultibaseUtils;
import org.omnione.did.oid4vc.oid4vp.util.jar.jws.CompactSigner;

import java.security.PrivateKey;
import org.omnione.did.verifier.v1.admin.service.VerifierInfoQueryService;
import org.omnione.did.verifier.v1.agent.service.DidDocService;
import org.omnione.did.verifier.v1.agent.service.FileWalletService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.common.service.VpSubmitAuditService;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseResult;
import org.omnione.did.verifier.v1.protocol.security.MdocTrustAnchorLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OID4VPService {

    private final AuthorizationService authorizationService;
    private final OID4VPHelperService oid4VPHelperService;
    private final MdocTrustAnchorLoader mdocTrustAnchorLoader;
    private final Oid4vpSessionMappingRepository sessionMappingRepository;
    private final Oid4vpSessionJpaRepository oid4vpSessionJpaRepository;
    private final TransactionService transactionService;
    private final FileWalletService fileWalletService;
    private final VerifierInfoQueryService verifierInfoQueryService;
    private final DidDocService didDocService;
    private final ObjectMapper objectMapper;
    private final VpSubmitAuditService vpSubmitAuditService;

    /**
     * Authorization Request JWT 조회 (Wallet이 request_uri로 호출)
     * mso_mdoc 포맷이 DCQL에 포함된 경우 x509_san_dns 경로(x5c 서명)를 사용하고,
     * 그 외에는 FileWallet의 assert 키로 DID 기반 JWT 서명을 수행한다.
     */
    public ServiceResult<String> getAuthorizationRequest(String requestId) {
        log.debug("=== OID4VP getAuthorizationRequest for requestId: {} ===", requestId);

        Oid4vpSessionMapping mapping = sessionMappingRepository.findByOid4vpRequestId(requestId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());
        validateTransaction(transaction);

        // 세션의 DCQL 쿼리로 포맷 확인
        Oid4vpSession oid4vpSession = oid4vpSessionJpaRepository.findByState(mapping.getState())
                .orElse(null);
        boolean useMdocScheme = oid4vpSession != null && containsMdocFormat(oid4vpSession.getDcqlQuery());

        ServiceResult<String> result;
        if (useMdocScheme) {
            result = buildX509AuthorizationRequest(requestId);
        } else {
            result = buildDidAuthorizationRequest(requestId);
        }

        if (!result.isSuccess()) {
            log.error("Failed to get authorization request: {} - {}", result.getErrorCode(), result.getErrorDescription());
            throw new OpenDidException(ErrorCode.OID4VP_AUTHORIZATION_REQUEST_FAILED);
        }

        log.debug("*** Authorization request retrieved for requestId: {} ***", requestId);
        return result;
    }

    /** DID 기반 서명 (decentralized_identifier scheme) */
    private ServiceResult<String> buildDidAuthorizationRequest(String requestId) {
        VerifierInfo verifierInfo = verifierInfoQueryService.getVerifierInfo();
        DidDocument verifierDidDoc = didDocService.getDidDocument(verifierInfo.getDid());

        String keyId = ProofPurpose.ASSERTION_METHOD.toKeyId();
        VerificationMethod vm = BaseCoreDidUtil.getVerificationMethod(verifierDidDoc, keyId);
        String verificationMethod = verifierDidDoc.getId() + "?versionId=" + verifierDidDoc.getVersionId() + "#" + vm.getId();
        String publicKeyMultibase = vm.getPublicKeyMultibase();

        // CompactSigner는 이미 SHA-256된 hash를 넘기므로 재해시 없는 FromHash로 서명한다(이중 해시 방지).
        CompactSigner compactSigner = (kid, hash) -> fileWalletService.generateCompactSignatureFromHash(kid, hash);

        return authorizationService.getAuthorizationRequest(
                requestId, compactSigner, verificationMethod, publicKeyMultibase);
    }

    /** x509_san_dns 기반 서명 (mdoc 흐름): verifier_x509 키/인증서로 x5c 헤더 포함 JWS 생성 */
    private ServiceResult<String> buildX509AuthorizationRequest(String requestId) {
        PrivateKey privateKey = mdocTrustAnchorLoader.getVerifierPrivateKey();
        X509Certificate verifierCert = mdocTrustAnchorLoader.getVerifierCertificate();
        if (privateKey == null || verifierCert == null) {
            log.error("Verifier x509 key/cert not loaded — cannot build x509_san_dns Authorization Request");
            throw new OpenDidException(ErrorCode.OID4VP_AUTHORIZATION_REQUEST_FAILED);
        }
        try {
            String certBase64 = Base64.getEncoder().encodeToString(verifierCert.getEncoded());
            List<String> x5cChain = List.of(certBase64);
            log.debug("Building x509_san_dns Authorization Request with x5c chain (leaf SAN: {})",
                    verifierCert.getSubjectX500Principal().getName());
            return authorizationService.getAuthorizationRequest(requestId, privateKey, x5cChain);
        } catch (Exception e) {
            log.error("Failed to encode verifier certificate for x5c header", e);
            throw new OpenDidException(ErrorCode.OID4VP_AUTHORIZATION_REQUEST_FAILED);
        }
    }

    /**
     * VP Token 응답 처리 (Wallet이 vp_token 제출)
     * DCQL 쿼리의 credential id를 세션에서 읽어 vpTokenMap 키로 동적 매핑
     */
    @Transactional
    public Oid4vpResponseResult receiveResponse(Oid4vpResponseRequest request) {
        return processResponse(request.getState(), request.getVpToken(),
                request.getError(), request.getErrorDescription());
    }

    /**
     * VP Token 검증 처리(기존 receiveResponse 본문, 무변경). state로 세션 매핑을 조회해 Transaction/VpSubmit에 매핑한다.
     * DCQL 쿼리의 credential id를 세션에서 읽어 vpTokenMap 키로 동적 매핑한다.
     */
    Oid4vpResponseResult processResponse(String state, String vpTokenJson, String error, String errorDescription) {
        log.debug("=== OID4VP receiveResponse for state: {} ===", state);

        // 1. state로 세션 매핑 조회 (매핑이 없으면 Transaction도 특정할 수 없어 VpSubmit 저장 불가)
        Oid4vpSessionMapping mapping = sessionMappingRepository.findByState(state)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());

        try {
            // 2. Transaction 유효성 확인
            validateTransaction(transaction);

            // 3. oid4vp_session에서 DCQL 쿼리 조회 → credential ID 동적 추출
            Oid4vpSession oid4vpSession = oid4vpSessionJpaRepository.findByState(state)
                    .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

            String dcqlQuery = oid4vpSession.getDcqlQuery();
            // vp_token은 DCQL 응답 형식({credentialId: [VP, ...]})이므로 SDK 파서로 그대로 해석한다.
            // (자체 매핑 시 vp_token 전체를 다시 credentialId 키에 넣어 이중 래핑되는 문제가 있었음)
            Map<String, List<Object>> vpTokenMap = oid4VPHelperService.parseVPToken(vpTokenJson);
            log.debug("vpTokenMap keys: {}", vpTokenMap.keySet());

            boolean hasMdoc = containsMdocFormat(dcqlQuery);
            String vpFormat = resolveSubmitFormat(dcqlQuery);
            List<String> issuerPublicKeys = resolveIssuerPublicKeys(vpTokenMap);
            List<String> holderPublicKeys = resolveHolderPublicKeys(vpTokenMap);
            String holderDid = resolveHolderDid(vpTokenMap).orElse(null);
            log.debug("Resolved issuerPublicKeys: {}, holderPublicKeys: {}, holderDid: {}, hasMdoc: {}",
                    issuerPublicKeys.size(), holderPublicKeys.size(), holderDid, hasMdoc);

            ServiceResult<Map<String, Object>> result;
            if (hasMdoc) {
                // mdoc: x5c 기반 검증 — trustedRoots를 직접 전달
                List<X509Certificate> trustedRoots = mdocTrustAnchorLoader.getTrustedRoots();
                log.debug("Using trustedRoots ({} certs) for mdoc VP verification", trustedRoots.size());
                result = oid4VPHelperService.handleVPToken(
                        vpTokenMap,
                        issuerPublicKeys.isEmpty() ? List.of("unresolved-issuer-key") : issuerPublicKeys,
                        holderPublicKeys.isEmpty() ? null : holderPublicKeys,
                        trustedRoots,
                        state
                );
            } else {
                result = authorizationService.receiveResponse(
                        vpTokenMap,
                        issuerPublicKeys.isEmpty() ? List.of("unresolved-issuer-key") : issuerPublicKeys,
                        holderPublicKeys.isEmpty() ? null : holderPublicKeys,
                        state,
                        error,
                        errorDescription,
                        "POST"
                );
            }

            if (result.isSuccess()) {
                vpSubmitAuditService.recordSuccess(transaction.getId(), vpTokenJson, holderDid, vpFormat);
                transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.COMPLETED);
                log.debug("*** OID4VP response processed. txId={}, status=COMPLETED ***", mapping.getTxId());

                return Oid4vpResponseResult.builder()
                        .sessionId(mapping.getTxId())
                        .status("COMPLETED")
                        .build();
            }

            vpSubmitAuditService.recordFailure(transaction.getId(), vpTokenJson, holderDid, result.getErrorCode(), vpFormat);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            log.error("OID4VP response failed: {} - {}", result.getErrorCode(), result.getErrorDescription());

            return Oid4vpResponseResult.builder()
                    .sessionId(mapping.getTxId())
                    .status("FAILED")
                    .error(result.getErrorCode())
                    .errorDescription(result.getErrorDescription())
                    .build();

        } catch (OpenDidException e) {
            vpSubmitAuditService.recordFailure(transaction.getId(), vpTokenJson, null, e.getErrorCode().getCode(), null);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            throw e;
        } catch (OID4VPException e) {
            // vp_token 파싱 등 SDK 처리 실패: 실패로 기록하고 FAILED 결과 반환(컨트롤러에서 500으로 변환)
            log.error("OID4VP response failed: {} - {}", e.getErrorCode(), e.getErrorMsg(), e);
            vpSubmitAuditService.recordFailure(transaction.getId(), vpTokenJson, null, e.getErrorCode(), null);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            return Oid4vpResponseResult.builder()
                    .sessionId(mapping.getTxId())
                    .status("FAILED")
                    .error(e.getErrorCode())
                    .errorDescription(e.getErrorMsg())
                    .build();
        }
    }

    /**
     * vpTokenMap에서 holder DID를 추출한다. 포맷별 verificationMethod/kid를 파싱하여 DID 부분만 남긴다.
     * 여러 credential이 있으면 첫 번째로 해석되는 DID를 반환한다.
     */
    private Optional<String> resolveHolderDid(Map<String, List<Object>> vpTokenMap) {
        if (vpTokenMap == null || vpTokenMap.isEmpty()) return Optional.empty();
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                VpTokenFormat format = detectFormat(credential);
                Optional<String> did = switch (format) {
                    case JSON_VP -> extractHolderDidFromJsonVp(credential);
                    case SD_JWT -> extractHolderDidFromSdJwt((String) credential);
                    default -> Optional.empty();
                };
                if (did.isPresent()) return did;
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractHolderDidFromJsonVp(Object credential) {
        try {
            JsonNode vp = toJsonNode(credential);
            JsonNode proof = vp.get("proof");
            if (proof == null) return Optional.empty();
            JsonNode vmNode = proof.get("verificationMethod");
            if (vmNode == null) return Optional.empty();
            return Optional.of(extractDid(vmNode.asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractHolderDidFromSdJwt(String sdJwt) {
        try {
            String issuerJwt = sdJwt.split("~", 2)[0];
            String[] jwtParts = issuerJwt.split("\\.");
            if (jwtParts.length < 2) return Optional.empty();
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(jwtParts[1]));
            JsonNode cnf = payload.get("cnf");
            if (cnf == null) return Optional.empty();
            JsonNode kidNode = cnf.get("kid");
            if (kidNode == null) return Optional.empty();
            return Optional.of(extractDid(kidNode.asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
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
     *   - 그 외 base64url 형태의 String: MDOC (base64url-CBOR)
     */
    private VpTokenFormat detectFormat(Object credential) {
        if (credential instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{")) return VpTokenFormat.JSON_VP;
            if (t.contains("~")) return VpTokenFormat.SD_JWT;
            // base64url without separators → likely MDOC CBOR
            if (t.matches("[A-Za-z0-9_=-]+")) return VpTokenFormat.MDOC;
            return VpTokenFormat.UNKNOWN;
        }
        if (credential instanceof Map) return VpTokenFormat.JSON_VP;
        return VpTokenFormat.UNKNOWN;
    }

    /**
     * DCQL 쿼리에서 대표 format 값을 추출한다. VpSubmit.format 저장에 사용.
     * 복수 format이 혼재하는 경우 첫 번째 credentials[0].format을 반환한다.
     */
    private String resolveSubmitFormat(String dcqlQueryJson) {
        if (dcqlQueryJson == null) return null;
        try {
            JsonNode dcql = objectMapper.readTree(dcqlQueryJson);
            JsonNode credentials = dcql.get("credentials");
            if (credentials != null && credentials.isArray() && !credentials.isEmpty()) {
                JsonNode formatNode = credentials.get(0).get("format");
                if (formatNode != null && !formatNode.isNull()) {
                    return formatNode.asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve submit format from DCQL query: {}", e.getMessage());
        }
        return null;
    }

    /**
     * DCQL 쿼리에 mso_mdoc 포맷의 credential이 포함되어 있는지 확인한다.
     * trustedRoots 경로(x5c 검증) 사용 여부를 결정하는 데 사용한다.
     */
    private boolean containsMdocFormat(String dcqlQueryJson) {
        if (dcqlQueryJson == null) return false;
        try {
            JsonNode dcql = objectMapper.readTree(dcqlQueryJson);
            JsonNode credentials = dcql.get("credentials");
            if (credentials == null || !credentials.isArray()) return false;
            for (JsonNode cred : credentials) {
                JsonNode formatNode = cred.get("format");
                if (formatNode != null && "mso_mdoc".equals(formatNode.asText())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse DCQL query for mdoc format check: {}", e.getMessage());
        }
        return false;
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
