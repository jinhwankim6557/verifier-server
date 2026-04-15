package org.omnione.did.verifier.v1.protocol.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.omnione.did.verifier.v1.protocol.handler.ProtocolHandler;
import org.omnione.did.verifier.v1.protocol.registry.ProtocolRegistry;
import org.omnione.did.verifier.v1.protocol.resolver.PolicyProtocolResolver;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationOrchestrator {

    private final PolicyProtocolResolver policyProtocolResolver;
    private final ProtocolRegistry protocolRegistry;

    public InitiateResponse initiate(InitiateRequest request) {
        log.debug("=== VerificationOrchestrator.initiate policyId={} ===", request.getPolicyId());

        try {
            // 1. Policy → ProtocolType 결정
            ProtocolType protocolType = policyProtocolResolver.resolve(request.getPolicyId());

            // 2. Handler 조회
            ProtocolHandler handler = protocolRegistry.getHandler(protocolType);

            // 3. 위임
            return handler.initiate(request);

        } catch (OpenDidException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initiate verification for policyId={}", request.getPolicyId(), e);
            throw new OpenDidException(ErrorCode.FAILED_TO_INITIATE_VERIFICATION);
        }
    }
}
