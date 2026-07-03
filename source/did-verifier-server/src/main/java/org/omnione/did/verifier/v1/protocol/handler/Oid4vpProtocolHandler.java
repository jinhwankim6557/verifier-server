package org.omnione.did.verifier.v1.protocol.handler;

import com.nimbusds.jose.jwk.ECKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.repository.Oid4vpSessionJpaRepository;
import org.omnione.did.base.db.repository.Oid4vpSessionMappingRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.service.InitiationService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.common.PolicyCacheService;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.omnione.did.verifier.v1.protocol.security.Oid4vpEncKeyManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class Oid4vpProtocolHandler implements ProtocolHandler {

    private final PolicyCacheService policyCacheService;
    private final TransactionService transactionService;
    private final Oid4vpSessionMappingRepository oid4vpSessionMappingRepository;
    private final Oid4vpSessionJpaRepository oid4vpSessionJpaRepository;
    private final InitiationService initiationService;
    private final Oid4vpEncKeyManager encKeyManager;

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OID4VP;
    }

    @Override
    @Transactional
    public InitiateResponse initiate(InitiateRequest request) {
        log.debug("=== OID4VP initiate for policyId: {} ===", request.getPolicyId());

        try {
            // 1. Policy 조회 (cached)
            Policy policy = policyCacheService.findByPolicyId(request.getPolicyId());

            // 2. Scope 확인
            String scope = policy.getScope();
            if (scope == null || scope.isBlank()) {
                throw new OpenDidException(ErrorCode.DCQL_SCOPE_MAPPING_NOT_FOUND);
            }

            // 3. 트랜잭션별 임시 enc 키쌍 생성 → client_metadata에 공개키 주입
            ECKey ephemeralKeyPair = encKeyManager.generateEphemeralKeyPair();
            String clientMetadataJson = encKeyManager.buildClientMetadataJson(ephemeralKeyPair);

            // 4. SDK InitiationService 호출 (direct_post.jwt + enc jwks 주입)
            ServiceResult<Map<String, Object>> result = initiationService.initiateVerification(
                    null,                  // dcqlQuery (scope 기반이므로 null)
                    scope,                 // scope
                    "direct_post.jwt",     // responseMode — JWE 암호화 응답 요구
                    clientMetadataJson,    // clientMetadata — enc jwks + encrypted_response_enc_values_supported
                    true                   // useRequestUri
            );

            if (!result.isSuccess()) {
                log.error("SDK initiation failed: {} - {}", result.getErrorCode(), result.getErrorDescription());
                throw new OpenDidException(ErrorCode.OID4VP_INITIATION_FAILED);
            }

            Map<String, Object> sdkResponse = result.getData();

            String oid4vpTransactionId = (String) sdkResponse.get("transaction_id");
            String requestId = (String) sdkResponse.get("request_id");
            String state = (String) sdkResponse.get("state");
            String authorizationRequestUri = (String) sdkResponse.get("authorization_request_uri");

            // 5. 통합 Transaction 생성
            Transaction transaction = transactionService.insertTransaction(Transaction.builder()
                    .txId(UUID.randomUUID().toString())
                    .type(TransactionType.OID4VP)
                    .status(TransactionStatus.PENDING)
                    .expired_at(transactionService.retrieveTransactionExpiredTime())
                    .build());

            // 6. 세션 매핑 저장 (통합 txId ↔ SDK 세션 ID)
            oid4vpSessionMappingRepository.save(Oid4vpSessionMapping.builder()
                    .txId(transaction.getTxId())
                    .oid4vpTransactionId(oid4vpTransactionId)
                    .oid4vpRequestId(requestId)
                    .state(state)
                    .build());

            // 7. SDK가 생성한 oid4vp_session 행에 임시 개인키 보관 (kid로 복호화 시 조회)
            persistEncKey(oid4vpSessionJpaRepository, encKeyManager, requestId, ephemeralKeyPair);

            log.debug("*** OID4VP initiate completed. txId={}, oid4vpTxId={}, requestId={} ***",
                    transaction.getTxId(), oid4vpTransactionId, requestId);

            return InitiateResponse.builder()
                    .protocol(ProtocolType.OID4VP)
                    .sessionId(transaction.getTxId())
                    .authorizationRequest(authorizationRequestUri)
                    .nextEndpoints(Map.of(
                            "authorizationRequest", "/oid4vp/request/" + requestId,
                            "response", "/oid4vp/response"
                    ))
                    .build();

        } catch (OpenDidException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initiate OID4VP verification", e);
            throw new OpenDidException(ErrorCode.OID4VP_INITIATION_FAILED);
        }
    }

    /** SDK가 initiateVerification 중 생성한 oid4vp_session 행을 requestId로 찾아 임시 개인키를 채운다. */
    static void persistEncKey(Oid4vpSessionJpaRepository repository, Oid4vpEncKeyManager encKeyManager,
                               String requestId, ECKey ephemeralKeyPair) {
        Oid4vpSession session = repository.findByRequestId(requestId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.OID4VP_SESSION_MAPPING_NOT_FOUND));
        session.setEncKid(ephemeralKeyPair.getKeyID());
        session.setEncPrivateKeyJwk(encKeyManager.toStorableJwk(ephemeralKeyPair));
        repository.save(session);
    }
}
