package org.omnione.did.verifier.v1.agent.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.Payload;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.domain.PolicyProfile;
import org.omnione.did.base.db.domain.VpFilter;
import org.omnione.did.base.db.domain.VpProcess;
import org.omnione.did.base.db.repository.PayloadRepository;
import org.omnione.did.base.db.repository.PolicyProfileRepository;
import org.omnione.did.base.db.repository.VpFilterRepository;
import org.omnione.did.base.db.repository.VpProcessRepository;
import org.omnione.did.verifier.v1.common.PolicyCacheService;
import org.omnione.did.base.property.VerifierProperty;
import org.omnione.did.data.model.profile.Filter;
import org.omnione.did.data.model.profile.verify.VerifyProcess;
import org.omnione.did.data.model.provider.ProviderDetail;
import org.omnione.did.data.model.vc.CredentialSchema;
import org.omnione.did.verifier.v1.provider.VerificationConfigProvider;
import org.omnione.did.verifier.v1.model.policy.VerificationPolicy;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

/**
 * SDK VerificationConfigProvider 구현체 (Adapter 패턴)
 *
 * DB에서 Policy, Filter, Process 정보를 조회하여 SDK DTO(VerificationPolicy)로 변환합니다.
 * 변환된 DTO의 filter, process, verifier 필드는 Core 라이브러리 타입을 직접 사용합니다.
 *
 * 설계 원칙:
 * - 어댑터는 변환만 담당 (비즈니스 로직 없음)
 * - Core 라이브러리 타입 직접 반환 (SDK 중간 DTO 사용 금지)
 * - JSON 직렬화 없이 필드 직접 매핑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationConfigProviderImpl implements VerificationConfigProvider {

    private final PolicyCacheService policyCacheService;
    private final PolicyProfileRepository policyProfileRepository;
    private final VpFilterRepository vpFilterRepository;
    private final VpProcessRepository vpProcessRepository;
    private final PayloadRepository payloadRepository;
    private final VerifierProperty verifierProperty;
    private final ObjectMapper objectMapper;

    /**
     * Policy ID로 Verification Policy 조회
     *
     * @param policyId Policy ID
     * @return VerificationPolicy (filter, process, verifier가 Core 타입)
     * @throws VerifierSdkException Policy 미존재 시 또는 조회 실패 시
     */
    @Override
    public VerificationPolicy getPolicy(String policyId) {
        try {
            // 1. Policy 조회 (cached)
            Policy policy;
            try {
                policy = policyCacheService.findByPolicyId(policyId);
            } catch (Exception e) {
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_POLICY_NOT_FOUND,
                        "Policy not found: " + policyId);
            }

            // 2. PolicyType 확인 - ZKP는 이 메소드에서 조회 불가
            if (policy.getPolicyType() == org.omnione.did.base.db.constant.PolicyType.ZKP) {
                log.warn("getPolicy() called with ZKP Policy ID: {}. This method only supports VP Policy.", policyId);
                throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_POLICY_NOT_FOUND,
                    "ZKP Policy cannot be retrieved through this method. " +
                    "Policy ID: " + policyId + ". " +
                    "Use /request-proof-request-profile API for ZKP verification."
                );
            }

            // 3. PolicyProfile 조회 (VP Policy만)
            PolicyProfile policyProfile = policyProfileRepository.findByPolicyProfileId(policy.getPolicyProfileId())
                    .orElseThrow(() -> new VerifierSdkException(
                            VerifierSdkErrorCode.SDK_INVALID_POLICY_CONFIGURATION,
                            "Policy profile not found: " + policy.getPolicyProfileId()));

            // 4. VpFilter 조회
            VpFilter vpFilter = vpFilterRepository.findById(policyProfile.getFilterId())
                    .orElseThrow(() -> new VerifierSdkException(
                            VerifierSdkErrorCode.SDK_INVALID_POLICY_CONFIGURATION,
                            "Filter not found for ID: " + policyProfile.getFilterId()));

            // 5. VpProcess 조회
            VpProcess vpProcess = vpProcessRepository.findById(policyProfile.getProcessId())
                    .orElseThrow(() -> new VerifierSdkException(
                            VerifierSdkErrorCode.SDK_INVALID_POLICY_CONFIGURATION,
                            "Process not found for ID: " + policyProfile.getProcessId()));

            // 6. Payload 조회
            Payload payload = payloadRepository.findByPayloadId(policy.getPayloadId())
                    .orElseThrow(() -> new VerifierSdkException(
                            VerifierSdkErrorCode.SDK_INVALID_POLICY_CONFIGURATION,
                            "Payload not found for ID: " + policy.getPayloadId()));

            // 7. VerificationPolicy DTO 조립 (Core 타입 직접 사용)
            return buildVerificationPolicy(policy, policyProfile, vpFilter, vpProcess, payload);

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve policy: {}", policyId, e);
            throw new VerifierSdkException(VerifierSdkErrorCode.SDK_CONFIGURATION_ERROR,
                    "Failed to retrieve policy configuration: " + e.getMessage());
        }
    }

    @Override
    public boolean existsPolicy(String policyId) {
        try {
            return policyCacheService.findOptionalByPolicyId(policyId).isPresent();
        } catch (Exception e) {
            log.debug("Error checking policy existence: {}", policyId, e);
            return false;
        }
    }

    /**
     * Policy, PolicyProfile, VpFilter, VpProcess, Payload를 VerificationPolicy DTO로 조립
     * filter, process, verifier는 Core 라이브러리 타입으로 직접 구성
     */
    private VerificationPolicy buildVerificationPolicy(
            Policy policy,
            PolicyProfile policyProfile,
            VpFilter vpFilter,
            VpProcess vpProcess,
            Payload payload) {

        return VerificationPolicy.builder()
                .policyId(policy.getPolicyId())
                .policyName(policy.getPolicyTitle())
                .description(policyProfile.getDescription())
                .language(policyProfile.getLanguage())
                .mode(payload.getMode() != null ? payload.getMode().toString() : "Direct")
                .validityDuration(payload.getValidSecond())
                .endpoints(parseEndpoints(payload.getEndpoints()))
                // Core 타입으로 직접 구성 (SDK 중간 DTO 없음)
                .filter(buildFilter(vpFilter))
                .process(buildVerifyProcess(vpProcess))
                .verifier(buildProviderDetail())
                .build();
    }

    /**
     * Endpoints JSON 문자열을 List로 파싱
     */
    private List<String> parseEndpoints(String endpointsJson) {
        try {
            if (endpointsJson == null || endpointsJson.isEmpty()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(endpointsJson, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse endpoints JSON: {}", endpointsJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * VpFilter Entity → Core Filter (필드 직접 매핑, JSON 직렬화 없음)
     */
    private Filter buildFilter(VpFilter vpFilter) {
        CredentialSchema schema = new CredentialSchema();
        schema.setId(vpFilter.getId());
        schema.setType(vpFilter.getType());
        schema.setValue(vpFilter.getValue());
        schema.setPresentAll(vpFilter.isPresent_all());
        schema.setDisplayClaims(vpFilter.getDisplayClaims() != null
                ? vpFilter.getDisplayClaims()
                : Collections.emptyList());
        schema.setRequiredClaims(vpFilter.getRequiredClaims() != null
                ? vpFilter.getRequiredClaims()
                : Collections.emptyList());
        schema.setAllowedIssuers(vpFilter.getAllowedIssuers() != null
                ? vpFilter.getAllowedIssuers()
                : Collections.emptyList());

        Filter filter = new Filter();
        filter.setCredentialSchemas(List.of(schema));
        return filter;
    }

    /**
     * VpProcess Entity → Core VerifyProcess (기본 정보만, reqE2e/verifierNonce 제외)
     * reqE2e와 verifierNonce는 SDK의 DefaultVpProfileService가 런타임에 동적으로 추가
     */
    private VerifyProcess buildVerifyProcess(VpProcess vpProcess) {
        VerifyProcess process = new VerifyProcess();
        process.setEndpoints(vpProcess.getEndpoints() != null
                ? vpProcess.getEndpoints()
                : Collections.emptyList());
        process.setAuthType(vpProcess.getAuthType());
        return process;
    }

    /**
     * Verifier 정보 → Core ProviderDetail (application.yml에서 로드)
     */
    private ProviderDetail buildProviderDetail() {
        ProviderDetail provider = new ProviderDetail();
        provider.setDid(verifierProperty.getDid());
        provider.setName(verifierProperty.getName());
        provider.setCertVcRef(verifierProperty.getCertVcRef());
        provider.setRef(verifierProperty.getRef());
        return provider;
    }
}
