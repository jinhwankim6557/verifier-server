package org.omnione.did.verifier.v1.protocol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.dto.IdentifierResult;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.MDocVPVerifier;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPException;
import org.omnione.did.oid4vc.oid4vp.service.AuthorizationService;
import org.omnione.did.oid4vc.oid4vp.service.OID4VPHelperService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.JweResponseDecryptor;
import org.omnione.did.oid4vc.oid4vp.util.crypto.MultibaseUtils;
import org.omnione.did.oid4vc.oid4vp.util.jar.jws.CompactSigner;

import java.security.interfaces.ECPrivateKey;
import org.omnione.did.verifier.v1.admin.service.VerifierInfoQueryService;
import org.omnione.did.verifier.v1.agent.service.DidDocService;
import org.omnione.did.verifier.v1.agent.service.FileWalletService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.common.service.VpSubmitAuditService;
import org.omnione.did.verifier.v1.protocol.service.status.CredentialStatusChecker;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseResult;
import org.omnione.did.verifier.v1.protocol.security.Oid4vpEncKeyManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OID4VPService {

    private final AuthorizationService authorizationService;
    private final OID4VPHelperService oid4VPHelperService;
    private final Oid4vpSessionMappingRepository sessionMappingRepository;
    private final Oid4vpSessionJpaRepository oid4vpSessionJpaRepository;
    private final TransactionService transactionService;
    private final FileWalletService fileWalletService;
    private final VerifierInfoQueryService verifierInfoQueryService;
    private final DidDocService didDocService;
    private final ObjectMapper objectMapper;
    private final VpSubmitAuditService vpSubmitAuditService;
    private final Oid4vpEncKeyManager encKeyManager;
    private final JweResponseDecryptor jweResponseDecryptor;
    private final CredentialStatusChecker credentialStatusChecker;

    /**
     * Authorization Request JWT 조회 (Wallet이 request_uri로 호출)
     * 이 검증자는 포맷과 무관하게 DID 기반 서명(decentralized_identifier scheme)만 사용한다.
     * FileWallet의 assert 키로 JWT를 서명한다.
     */
    public ServiceResult<String> getAuthorizationRequest(String requestId) {
        log.debug("=== OID4VP getAuthorizationRequest for requestId: {} ===", requestId);

        Oid4vpSessionMapping mapping = sessionMappingRepository.findByOid4vpRequestId(requestId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());
        validateTransaction(transaction);

        ServiceResult<String> result = buildDidAuthorizationRequest(requestId);

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

    /**
     * VP Token 응답 처리 (Wallet이 vp_token 제출)
     * DCQL 쿼리의 credential id를 세션에서 읽어 vpTokenMap 키로 동적 매핑
     */
    @Transactional
    public Oid4vpResponseResult receiveResponse(Oid4vpResponseRequest request) {
        if (isEncrypted(request)) {
            return receiveEncryptedResponse(request.getResponse());
        }
        rejectIfEncryptionRequired(request.getState());
        return processResponse(request.getState(), request.getVpToken(),
                request.getError(), request.getErrorDescription());
    }

    static boolean isEncrypted(Oid4vpResponseRequest request) {
        return request.getResponse() != null && !request.getResponse().isBlank();
    }

    /**
     * direct_post.jwt로 개시된 세션인데 response(JWE) 없이 평문으로 제출되면 거부한다.
     * 이 체크가 없으면 response_mode=direct_post.jwt는 Wallet에게 "이렇게 해달라"는 광고에
     * 그치고 서버가 실제로 강제하지 않는 셈이라, JWE를 도입한 목적(로그·프록시 구간에서의
     * claim 평문 노출 방지)이 평문 제출 한 번으로 무력화된다.
     * state가 없거나 매핑된 세션이 없으면 여기서는 판단하지 않고 processResponse가
     * 기존과 동일하게 OID4VP_SESSION_MAPPING_NOT_FOUND로 처리하게 둔다.
     */
    private void rejectIfEncryptionRequired(String state) {
        if (state == null || state.isBlank()) return;
        oid4vpSessionJpaRepository.findByState(state).ifPresent(session -> {
            if ("direct_post.jwt".equals(session.getResponseMode())) {
                log.warn("Plaintext response rejected — session requires direct_post.jwt (state={})", state);
                throw new OpenDidException(ErrorCode.OID4VP_ENCRYPTED_RESPONSE_REQUIRED);
            }
        });
    }

    /**
     * direct_post.jwt(JWE) 응답 처리.
     * kid로 세션·임시 개인키를 먼저 특정한 뒤 SDK 복호화 유틸에 위임한다(§5.5 ①②는 통합서버, ③④는 SDK).
     * kid로 세션을 아직 못 찾은 단계(맨 첫 줄)는 transaction 자체를 알 수 없어 감사기록이 불가능하다.
     * 그 이후(세션 확보 후)의 실패는 평문 경로(processResponse)와 동일하게 VpSubmit 감사기록 및
     * Transaction FAILED 처리를 남긴다(최종 리뷰 지적 반영 — 부인방지 목적의 VP History가 암호화 경로에서만
     * 비어있으면 안 된다).
     */
    private Oid4vpResponseResult receiveEncryptedResponse(String jweCompact) {
        String kid = encKeyManager.extractKid(jweCompact);
        Oid4vpSession session = oid4vpSessionJpaRepository.findByEncKid(kid)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));

        // Oid4vpSession.transactionId는 통합 Transaction.txId와 다른 값이라(SDK 내부 세션 식별자),
        // processResponse와 동일하게 Oid4vpSessionMapping(state → 통합 txId)을 거쳐 조회한다.
        Oid4vpSessionMapping mapping = sessionMappingRepository.findByState(session.getState())
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));
        Transaction transaction = transactionService.findTransactionByTxId(mapping.getTxId());

        String state;
        String vpTokenJson;
        try {
            JweResponseDecryptor.DecryptedResponse decrypted;
            try {
                ECPrivateKey privateKey = encKeyManager.loadPrivateKey(session.getEncPrivateKeyJwk());
                decrypted = jweResponseDecryptor.decrypt(jweCompact, privateKey);
            } catch (OID4VPException e) {
                log.warn("OID4VP response decryption failed for kid={}: {}", kid, e.getErrorMsg());
                throw new OpenDidException(ErrorCode.OID4VP_RESPONSE_DECRYPTION_FAILED, e);
            }

            Map<String, Object> payload;
            try {
                payload = objectMapper.readValue(decrypted.getPlaintext(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Decrypted JWE payload is not valid JSON for kid={}", kid);
                throw new OpenDidException(ErrorCode.OID4VP_RESPONSE_DECRYPTION_FAILED, e);
            }

            // 의도적 범위 제외(최종 리뷰 결론, 오버사이트 아님): 이 메서드는 암호화된 error-only 응답
            // (vp_token 없이 {error, error_description, state}만 담은 페이로드)을 아직 지원하지 않는다.
            // 그런 페이로드는 정상 OAuth 에러로 처리되지 못하고 OID4VP_RESPONSE_DECRYPTION_FAILED로 거부된다.
            // 원 설계의 페이로드 계약은 성공 형태(vp_token/presentation_submission/state)만 정의했고,
            // 암호화된 error 응답 형태는 애초에 명세된 적이 없다.
            // 참고로 평문 경로(processResponse, Task 9에서 무변경 추출·승인됨)도 동일한 근본 한계를 이미 갖고 있다:
            // processResponse는 error/errorDescription을 authorizationService.receiveResponse(...)로 전달하지만,
            // 그보다 먼저 oid4VPHelperService.parseVPToken(vpTokenJson)을 무조건 호출하며, SDK의
            // OID4VPHelperService.parseVPToken(null)은 error 처리 기회를 주기 전에
            // OID4VPException(ERR_CODE_VP_TOKEN_NULL)을 즉시 던진다(SDK 소스 확인 완료, 2026-07-06).
            // 즉 error-only 응답을 우아하게 처리하지 못하는 것은 JWE 작업이 도입한 회귀가 아니라
            // 평문 경로에도 이미 존재하던 특성이며, 암호화 경로는 (더 이른 시점에) 다른 에러 코드로
            // 실패할 뿐이다. vp_token/error 모두 지원하려면 (a) processResponse의 error 우선 분기 추가와
            // (b) 이 메서드에서 error/error_description 추출·전달이 필요하며, 이는 Task 9의 승인된 로직을
            // 변경하는 별도 태스크/리뷰 대상이다.
            Object vpTokenValue = payload.get("vp_token");
            Object stateValue = payload.get("state");
            if (vpTokenValue == null || stateValue == null) {
                log.warn("Decrypted JWE payload missing vp_token/state for kid={}", kid);
                throw new OpenDidException(ErrorCode.OID4VP_RESPONSE_DECRYPTION_FAILED);
            }

            // 공개키는 client_metadata로 노출되므로 누구나 세션 A의 키로 암호화하면서 페이로드에 다른 세션의
            // state를 넣을 수 있다. kid로 찾은 세션과 복호화된 state가 다른 세션을 가리키면 거부해 매핑 오염을 막는다.
            if (!session.getState().equals(stateValue.toString())) {
                log.warn("Decrypted JWE state does not match session bound to kid={}: sessionState={}, payloadState={}",
                        kid, session.getState(), stateValue);
                throw new OpenDidException(ErrorCode.OID4VP_RESPONSE_DECRYPTION_FAILED);
            }

            state = stateValue.toString();
            vpTokenJson = vpTokenValue instanceof String
                    ? (String) vpTokenValue
                    : objectMapper.writeValueAsString(vpTokenValue);
        } catch (OpenDidException e) {
            vpSubmitAuditService.recordFailure(transaction.getId(), null, null, e.getErrorCode().getCode(), null);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            throw e;
        } catch (JsonProcessingException e) {
            OpenDidException wrapped = new OpenDidException(ErrorCode.OID4VP_RESPONSE_DECRYPTION_FAILED, e);
            vpSubmitAuditService.recordFailure(transaction.getId(), null, null, wrapped.getErrorCode().getCode(), null);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            throw wrapped;
        }

        // processResponse는 자기 자신의 성공/실패 감사기록·Transaction 상태 처리를 이미 완결하므로
        // (위 catch에 포함시키면 이중 기록됨) try 블록 밖에서 호출한다.
        return processResponse(state, vpTokenJson, null, null);
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

        // catch 블록에서도 감사기록에 쓸 수 있도록 try 밖에 선언한다. try 안에서 채워지기 전에 예외가 나면
        // null로 남는데, 이는 "아직 해석 전"이라는 의미라 이전 동작과 동일하다.
        String holderDid = null;
        String vpFormat = null;
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

            // 이 검증자는 DID(kid) 기반 credential만 받는다. x5chain 기반 mdoc은 여기서 거부한다.
            rejectX5cBasedMdoc(vpTokenMap);

            vpFormat = resolveSubmitFormat(dcqlQuery);
            List<String> issuerPublicKeys = resolveIssuerPublicKeys(vpTokenMap);
            List<String> holderPublicKeys = resolveHolderPublicKeys(vpTokenMap);
            holderDid = resolveHolderDid(vpTokenMap).orElse(null);
            log.debug("Resolved issuerPublicKeys: {}, holderPublicKeys: {}, holderDid: {}",
                    issuerPublicKeys.size(), holderPublicKeys.size(), holderDid);

            ServiceResult<Map<String, Object>> result = authorizationService.receiveResponse(
                    vpTokenMap,
                    issuerPublicKeys.isEmpty() ? List.of("unresolved-issuer-key") : issuerPublicKeys,
                    holderPublicKeys.isEmpty() ? null : holderPublicKeys,
                    state,
                    error,
                    errorDescription,
                    "POST"
            );

            if (result.isSuccess()) {
                credentialStatusChecker.checkAll(vpTokenMap);
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
            vpSubmitAuditService.recordFailure(transaction.getId(), vpTokenJson, holderDid, e.getErrorCode().getCode(), vpFormat);
            transactionService.updateTransactionStatus(transaction.getId(), TransactionStatus.FAILED);
            throw e;
        } catch (OID4VPException e) {
            // vp_token 파싱 등 SDK 처리 실패: 실패로 기록하고 FAILED 결과 반환(컨트롤러에서 500으로 변환)
            log.error("OID4VP response failed: {} - {}", e.getErrorCode(), e.getErrorMsg(), e);
            vpSubmitAuditService.recordFailure(transaction.getId(), vpTokenJson, holderDid, e.getErrorCode(), vpFormat);
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
                    case MDOC -> resolveIssuerKeyFromMdoc((String) credential).ifPresent(keys::add);
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
                    // mdoc의 holder 바인딩(DeviceAuth)은 MSO에 담긴 deviceKey로 검증하므로 해석할 키가 없다.
                    case MDOC -> log.debug("mDoc holder key is taken from the MSO deviceKey; nothing to resolve");
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
     * mso_mdoc credential이 x5chain 기반이면 거부한다.
     *
     * <p>이 검증자는 DID(kid) 기반 mdoc만 지원한다. x5c 검증은 SDK에 남아 있지만(다른 지갑 상호운용용)
     * 서버는 신뢰앵커(IACA)를 운용하지 않으므로, x5chain만 담긴 mdoc이 들어오면 SDK 안쪽에서
     * "Trusted root certificates required" 같은 엉뚱한 메시지로 실패하기 전에 여기서 명시적으로 끊는다.
     */
    private void rejectX5cBasedMdoc(Map<String, List<Object>> vpTokenMap) {
        if (vpTokenMap == null) return;
        for (List<Object> credentials : vpTokenMap.values()) {
            if (credentials == null) continue;
            for (Object credential : credentials) {
                if (detectFormat(credential) != VpTokenFormat.MDOC) continue;
                if (isX5cBasedMdoc((String) credential)) {
                    log.warn("Rejected x5chain-based mso_mdoc — this verifier accepts DID(kid)-based mdoc only");
                    throw new OpenDidException(ErrorCode.OID4VP_MDOC_X5C_NOT_SUPPORTED);
                }
            }
        }
    }

    /** mso_mdoc이 kid(DID) 없이 x5chain으로만 발급자를 식별하는지 판별한다. */
    static boolean isX5cBasedMdoc(String mdocBase64) {
        try {
            IdentifierResult issuer = new MDocVPVerifier().extractIssuerIdentifier(mdocBase64);
            return issuer != null && issuer.getType() == IdentifierResult.Type.MSO_MDOC_X5C;
        } catch (Exception e) {
            log.warn("Failed to classify mso_mdoc issuer identifier: {}", e.getMessage());
            return false;
        }
    }

    /**
     * mso_mdoc IssuerAuth의 kid(DID URL)로 발급자 공개키를 해석한다.
     * SD-JWT kid 경로와 동일하게 DID Document의 publicKeyMultibase를 base64로 변환해 SDK에 넘긴다.
     */
    private Optional<String> resolveIssuerKeyFromMdoc(String mdocBase64) {
        try {
            IdentifierResult issuer = new MDocVPVerifier().extractIssuerIdentifier(mdocBase64);
            if (issuer == null || issuer.getType() != IdentifierResult.Type.MSO_MDOC_KID) {
                log.warn("mso_mdoc has no kid(DID); cannot resolve issuer public key");
                return Optional.empty();
            }
            return resolveKeyByVerificationMethod(issuer.getValue());
        } catch (Exception e) {
            log.warn("Failed to resolve issuer public key from mso_mdoc: {}", e.getMessage());
            return Optional.empty();
        }
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
