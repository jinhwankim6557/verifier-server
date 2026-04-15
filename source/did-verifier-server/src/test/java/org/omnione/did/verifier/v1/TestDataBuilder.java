package org.omnione.did.verifier.v1;

import org.omnione.did.base.datamodel.enums.EccCurveType;
import org.omnione.did.base.datamodel.enums.OfferType;
import org.omnione.did.base.datamodel.enums.SymmetricCipherType;
import org.omnione.did.base.datamodel.enums.SymmetricPaddingType;
import org.omnione.did.base.db.constant.ProfileMode;
import org.omnione.did.base.db.constant.VerifierStatus;
import org.omnione.did.base.db.domain.*;

import java.util.Arrays;
import java.util.List;

/**
 * 통합 테스트용 테스트 데이터 빌더
 *
 * Policy, PolicyProfile, VpFilter, VpProcess 등의 테스트 데이터를 생성합니다.
 */
public class TestDataBuilder {

    /**
     * 기본 Policy 생성
     */
    public static Policy createTestPolicy(String policyId, String policyProfileId) {
        return Policy.builder()
                .policyId(policyId)
                .policyTitle("Test Policy - " + policyId)
                .policyProfileId(policyProfileId)
                .payloadId("payload-001")
                .build();
    }

    /**
     * 기본 PolicyProfile 생성
     */
    public static PolicyProfile createTestPolicyProfile(String policyProfileId, Long filterId, Long processId) {
        return PolicyProfile.builder()
                .policyProfileId(policyProfileId)
                .title("Test Profile")
                .description("Test Profile Description")
                .encoding("UTF-8")
                .format("JSON")
                .language("ko")
                .link("https://example.com/profile")
                .type("VerifyProfile")
                .value("{}")
                .filterId(filterId)
                .processId(processId)
                .build();
    }

    /**
     * 기본 VpFilter 생성
     */
    public static VpFilter createTestVpFilter(Long filterId) {
        return VpFilter.builder()
                .filterId(filterId)
                .id("credential-schema-001")
                .type("OsdSchemaCredential")
                .title("Test Credential Schema")
                .requiredClaims(Arrays.asList("name", "email", "phone"))
                .allowedIssuers(Arrays.asList("did:example:issuer-001", "did:example:issuer-002"))
                .displayClaims(Arrays.asList("name", "email"))
                .present_all(false)
                .value(null)
                .build();
    }

    /**
     * 기본 VpProcess 생성 (PIN Only)
     */
    public static VpProcess createTestVpProcessPinOnly(Long processId) {
        return VpProcess.builder()
                .id(processId)
                .title("PIN Only Process")
                .endpoints(Arrays.asList(
                    "https://api.example.com/verify",
                    "https://api-backup.example.com/verify"
                ))
                .authType(0x00000002)  // PIN only
                .curve(EccCurveType.SECP_256_R1)
                .cipher(SymmetricCipherType.AES_256_CBC)
                .padding(SymmetricPaddingType.PKCS5)
                .build();
    }

    /**
     * VpProcess 생성 (PIN AND BIO)
     */
    public static VpProcess createTestVpProcessPinAndBio(Long processId) {
        return VpProcess.builder()
                .id(processId)
                .title("PIN AND BIO Process")
                .endpoints(Arrays.asList("https://api.example.com/verify"))
                .authType(0x00008006)  // PIN AND BIO
                .curve(EccCurveType.SECP_256_R1)
                .cipher(SymmetricCipherType.AES_256_CBC)
                .padding(SymmetricPaddingType.PKCS5)
                .build();
    }

    /**
     * VpProcess 생성 (커스텀)
     */
    public static VpProcess createTestVpProcess(
            Long processId,
            String title,
            List<String> endpoints,
            int authType) {
        return VpProcess.builder()
                .id(processId)
                .title(title)
                .endpoints(endpoints)
                .authType(authType)
                .curve(EccCurveType.SECP_256_R1)
                .cipher(SymmetricCipherType.AES_256_CBC)
                .padding(SymmetricPaddingType.PKCS5)
                .build();
    }

    /**
     * 기본 Payload 생성
     */
    public static Payload createTestPayload(String payloadId) {
        return Payload.builder()
                .payloadId(payloadId)
                .service("TestService")
                .device("TestDevice")
                .locked(false)
                .mode(ProfileMode.Direct)
                .endpoints("[\"https://api.example.com/verify\"]")
                .validSecond(300)
                .offerType(OfferType.VerifyOffer)
                .build();
    }

    /**
     * 완전한 Policy 시스템 생성 (모든 관련 엔티티)
     */
    public static PolicyBundle createCompletePolicy(
            String policyId,
            String policyProfileId,
            Long filterId,
            Long processId) {

        String payloadId = "payload-001";

        Payload payload = createTestPayload(payloadId);
        Policy policy = createTestPolicy(policyId, policyProfileId);
        PolicyProfile policyProfile = createTestPolicyProfile(policyProfileId, filterId, processId);
        VpFilter vpFilter = createTestVpFilter(filterId);
        VpProcess vpProcess = createTestVpProcessPinOnly(processId);

        return new PolicyBundle(policy, policyProfile, vpFilter, vpProcess, payload);
    }

    /**
     * Policy 시스템 번들
     */
    public static class PolicyBundle {
        public final Policy policy;
        public final PolicyProfile policyProfile;
        public final VpFilter vpFilter;
        public final VpProcess vpProcess;
        public final Payload payload;

        public PolicyBundle(Policy policy, PolicyProfile policyProfile, VpFilter vpFilter, VpProcess vpProcess, Payload payload) {
            this.policy = policy;
            this.policyProfile = policyProfile;
            this.vpFilter = vpFilter;
            this.vpProcess = vpProcess;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return "PolicyBundle{" +
                    "policyId='" + policy.getPolicyId() + '\'' +
                    ", profileId='" + policyProfile.getPolicyProfileId() + '\'' +
                    ", filterId=" + policyProfile.getFilterId() +
                    ", processId=" + policyProfile.getProcessId() +
                    ", payloadId='" + payload.getPayloadId() + '\'' +
                    '}';
        }
    }

    /**
     * 테스트용 Transaction 생성
     */
    public static Transaction createTestTransaction(String txId) {
        return Transaction.builder()
                .txId(txId)
                .build();
    }

    /**
     * 테스트용 E2E 세션 생성
     */
    public static E2e createTestE2eSession(Long transactionId, String sessionKey, String nonce) {
        return E2e.builder()
                .transactionId(transactionId)
                .sessionKey(sessionKey)
                .nonce(nonce)
                .curve("Secp256r1")
                .cipher("AES-256-CBC")
                .padding("PKCS5")
                .build();
    }

    /**
     * VP Offer 생성
     */
    public static VpOffer createTestVpOffer(
            Long transactionId,
            String offerId,
            String policyId) {
        return VpOffer.builder()
                .transactionId(transactionId)
                .offerId(offerId)
                .vpPolicyId(policyId)
                .build();
    }

    /**
     * VerifierInfo 생성 (기본)
     */
    public static VerifierInfo createTestVerifierInfo() {
        return VerifierInfo.builder()
                .did("did:example:verifier-test-001")
                .name("Test Verifier")
                .status(VerifierStatus.ACTIVATE)
                .serverUrl("https://api.example.com/verifier")
                .certificateUrl("https://api.example.com/cert")
                .build();
    }

    /**
     * VerifierInfo 생성 (커스텀)
     */
    public static VerifierInfo createTestVerifierInfo(String did, String name) {
        return VerifierInfo.builder()
                .did(did)
                .name(name)
                .status(VerifierStatus.ACTIVATE)
                .serverUrl("https://api.example.com/verifier")
                .certificateUrl("https://api.example.com/cert")
                .build();
    }
}
