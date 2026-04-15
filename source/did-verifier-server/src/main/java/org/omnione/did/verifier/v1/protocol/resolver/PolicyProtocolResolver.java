package org.omnione.did.verifier.v1.protocol.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.verifier.v1.common.PolicyCacheService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyProtocolResolver {

    private final PolicyCacheService policyCacheService;

    public ProtocolType resolve(String policyId) {
        Policy policy = policyCacheService.findByPolicyId(policyId);

        ProtocolType protocolType = policy.getProtocolType();
        log.debug("Resolved policyId={} → protocolType={}", policyId, protocolType);
        return protocolType;
    }
}
