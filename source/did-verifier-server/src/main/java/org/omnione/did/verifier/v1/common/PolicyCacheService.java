package org.omnione.did.verifier.v1.common;

import lombok.RequiredArgsConstructor;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.repository.PolicyRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Policy 조회 캐싱 서비스.
 * 검증 플로우에서 동일 policyId로 여러 번 DB 조회하는 것을 방지한다.
 */
@Service
@RequiredArgsConstructor
public class PolicyCacheService {

    private final PolicyRepository policyRepository;

    @Cacheable(value = "policy", key = "#policyId")
    public Policy findByPolicyId(String policyId) {
        return policyRepository.findByPolicyId(policyId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.VP_POLICY_NOT_FOUND));
    }

    @Cacheable(value = "policy", key = "#policyId")
    public Optional<Policy> findOptionalByPolicyId(String policyId) {
        return policyRepository.findByPolicyId(policyId);
    }

    @CacheEvict(value = {"policy", "verificationPolicy"}, allEntries = true)
    public void evictAll() {
        // Cache eviction only
    }
}
