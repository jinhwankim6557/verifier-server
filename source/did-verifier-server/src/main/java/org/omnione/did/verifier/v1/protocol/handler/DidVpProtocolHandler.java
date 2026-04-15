package org.omnione.did.verifier.v1.protocol.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.verifier.v1.agent.dto.RequestOfferReqDto;
import org.omnione.did.verifier.v1.agent.dto.RequestOfferResDto;
import org.omnione.did.verifier.v1.agent.service.VpOfferApplicationService;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DidVpProtocolHandler implements ProtocolHandler {

    private final VpOfferApplicationService vpOfferApplicationService;

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.DID_VP;
    }

    @Override
    public InitiateResponse initiate(InitiateRequest request) {
        log.debug("=== DID VP initiate for policyId: {} ===", request.getPolicyId());

        RequestOfferReqDto offerReq = RequestOfferReqDto.builder()
                .policyId(request.getPolicyId())
                .build();

        RequestOfferResDto offerRes = vpOfferApplicationService.requestVpOfferbyQR(offerReq);

        return InitiateResponse.builder()
                .protocol(ProtocolType.DID_VP)
                .sessionId(offerRes.getTxId())
                .payload(offerRes.getPayload())
                .nextEndpoints(Map.of(
                        "requestProfile", "/verifier/api/v1/request-profile",
                        "requestVerify", "/verifier/api/v1/request-verify",
                        "confirmVerify", "/verifier/api/v1/confirm-verify"
                ))
                .build();
    }
}
