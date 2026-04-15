package org.omnione.did.verifier.v1.protocol.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.repository.Oid4vpSessionMappingRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.oid4vc.oid4vp.service.InitiationService;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.common.PolicyCacheService;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
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
    private final InitiationService initiationService;

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

            // 3. SDK InitiationService 호출
            ServiceResult<Map<String, Object>> result = initiationService.initiateVerification(
                    null,           // dcqlQuery (scope 기반이므로 null)
                    scope,          // scope
                    "direct_post",  // responseMode
                    null,           // clientMetadata (기본값 사용)
                    true            // useRequestUri
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

            // 4. 통합 Transaction 생성
            Transaction transaction = transactionService.insertTransaction(Transaction.builder()
                    .txId(UUID.randomUUID().toString())
                    .type(TransactionType.OID4VP)
                    .status(TransactionStatus.PENDING)
                    .expired_at(transactionService.retrieveTransactionExpiredTime())
                    .build());

            // 5. 세션 매핑 저장 (통합 txId ↔ SDK 세션 ID)
            oid4vpSessionMappingRepository.save(Oid4vpSessionMapping.builder()
                    .txId(transaction.getTxId())
                    .oid4vpTransactionId(oid4vpTransactionId)
                    .oid4vpRequestId(requestId)
                    .state(state)
                    .build());

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
}
