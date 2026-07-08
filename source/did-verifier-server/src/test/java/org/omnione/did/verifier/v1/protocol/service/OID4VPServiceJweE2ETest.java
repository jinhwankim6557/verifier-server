package org.omnione.did.verifier.v1.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.domain.DcqlScopeMapping;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.repository.DcqlScopeMappingRepository;
import org.omnione.did.base.db.repository.Oid4vpSessionJpaRepository;
import org.omnione.did.base.db.repository.PolicyRepository;
import org.omnione.did.oid4vc.oid4vp.service.ScopeToDCQLMapperService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.VPTokenEncryptor;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;
import org.omnione.did.verifier.v1.protocol.handler.Oid4vpProtocolHandler;
import org.omnione.did.verifier.v1.protocol.util.TestJweEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OID4VP JWE 응답 왕복 E2E 테스트.
 *
 * {@code test} 프로파일은 liquibase.enabled=false + hibernate.ddl-auto=create-drop이라
 * Liquibase seed(기본 Policy/DcqlScopeMapping insert)가 적용되지 않는다(스키마만 생성됨).
 * {@link Oid4vpProtocolHandler#initiate}가 {@code policyCacheService.findByPolicyId(...)}로 Policy를
 * 조회하고 그 scope를 SDK의 {@link ScopeToDCQLMapperService}로 DCQL 쿼리로 변환하므로, 이 테스트가
 * 직접 최소 유효 Policy + DcqlScopeMapping을 심어야 한다(VerifierSDKIntegrationTest의 TestDataBuilder
 * 컨벤션과 동일한 목적, DCQL/OID4VP 전용이라 별도 헬퍼 없이 인라인으로 구성).
 *
 * 주의: {@link ScopeToDCQLMapperService}는 {@code @PostConstruct}에서 DB로부터 매핑을 1회 캐싱하므로
 * (컨텍스트 기동 시점, 즉 이 테스트의 @BeforeEach보다 먼저 실행됨) @BeforeEach에서 새 DcqlScopeMapping
 * 행을 저장한 뒤 {@code reloadMappings()}를 명시적으로 호출해야 캐시에 반영된다 — 이는
 * {@code DcqlScopeMappingService}(관리자 API)가 저장 후 캐시를 갱신하는 것과 동일한 컨벤션이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OID4VPServiceJweE2ETest {

    private static final String TEST_POLICY_ID = "test-policy";
    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_DCQL_QUERY =
            "{\"credentials\":[{\"id\":\"cred1\",\"format\":\"jwt_vc_json\"}]}";

    @Autowired
    private Oid4vpProtocolHandler protocolHandler;
    @Autowired
    private OID4VPService oid4vpService;
    @Autowired
    private Oid4vpSessionJpaRepository sessionRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PolicyRepository policyRepository;
    @Autowired
    private DcqlScopeMappingRepository dcqlScopeMappingRepository;
    @Autowired
    private ScopeToDCQLMapperService scopeToDCQLMapperService;
    @Autowired
    private VPTokenEncryptor vpTokenEncryptor;

    private final TestJweEncryptor walletSimulator = new TestJweEncryptor();

    @BeforeEach
    void setUpOid4vpPolicy() {
        dcqlScopeMappingRepository.save(DcqlScopeMapping.builder()
                .scope(TEST_SCOPE)
                .dcqlQuery(TEST_DCQL_QUERY)
                .description("Task 12 E2E test scope mapping")
                .enabled(true)
                .build());
        // SDK 캐시는 컨텍스트 기동 시 1회만 로드되므로, 방금 심은 행을 보이게 하려면 명시적으로 reload한다.
        scopeToDCQLMapperService.reloadMappings();

        policyRepository.save(Policy.builder()
                .policyId(TEST_POLICY_ID)
                .policyTitle("Task 12 E2E Test Policy")
                .protocolType(ProtocolType.OID4VP)
                .scope(TEST_SCOPE)
                .build());
    }

    @Test
    @Transactional
    void jweResponse_decryptsAndReachesExistingVerificationPath() throws Exception {
        // 1. initiate — 임시 enc 키쌍 생성 + direct_post.jwt 세션 생성
        InitiateRequest initiateRequest = InitiateRequest.builder()
                .policyId(TEST_POLICY_ID)
                .build();
        InitiateResponse initiateResponse = protocolHandler.initiate(initiateRequest);
        assertThat(initiateResponse.getSessionId()).isNotBlank();

        // 2. 방금 생성된 세션에서 enc 공개키(JWK) 복원 — Wallet이 Authorization Request JWT에서 읽는 것과 동일한 값
        Oid4vpSession session = sessionRepository.findAll().stream()
                .filter(s -> s.getEncKid() != null)
                .reduce((first, second) -> second) // 가장 최근 생성분
                .orElseThrow();
        // enc_private_key_jwk는 VPTokenEncryptor로 암호화되어 저장되므로(at-rest 보호) 복호화 후 파싱한다.
        ECKey fullKey = ECKey.parse(vpTokenEncryptor.decrypt(session.getEncPrivateKeyJwk()));
        ECKey publicKey = fullKey.toPublicJWK();
        assertThat(publicKey.getKeyID()).isEqualTo(session.getEncKid());

        // 3. Wallet 시뮬레이터로 JWE 암호화 (미검증 통과용 최소 vp_token — 검증 실패는 허용, 복호화 성공만 확인)
        Map<String, Object> payload = Map.of(
                "vp_token", "{}",
                "presentation_submission", Map.of(),
                "state", session.getState()
        );
        String jweCompact = walletSimulator.encrypt(objectMapper.writeValueAsString(payload), publicKey);
        assertThat(JWEObject.parse(jweCompact).getHeader().getKeyID()).isEqualTo(session.getEncKid());

        // 4. /oid4vp/response 로 제출 — 복호화되어 기존 검증 경로(processResponse)까지 도달하는지 확인
        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                null, null, null, null, null, jweCompact);
        var result = oid4vpService.receiveResponse(request);

        // vp_token이 빈 값이라 VP 검증 자체는 실패하지만, "복호화되어 검증 단계까지 도달했다"는
        // FAILED 응답(예외가 아니라 정상적인 검증 실패 결과)으로 확인한다 — 복호화 실패였다면 OpenDidException이 던져진다.
        assertThat(result.getSessionId()).isNotBlank();
        assertThat(result.getStatus()).isIn("FAILED", "COMPLETED");
    }

    @Test
    @Transactional
    void jweResponse_unknownKid_throwsSessionMappingNotFound() throws Exception {
        // header={"alg":"ECDH-ES","enc":"A256GCM","kid":"no-such-kid"} + 5-part JWE 형태(빈 encryptedKey +
        // 나머지 3개 세그먼트는 더미). 헤더 파싱까지는 성공해야 kid 조회 단계에서 실패하는지 검증할 수 있다 —
        // 파트 개수가 5개가 아니면 JWEObject.parse 자체가 ParseException을 던져 "헤더 파싱 실패"로 끝나버려
        // 이 테스트가 검증하려는 "session mapping not found" 지점에 도달하지 못한다.
        String bogusJwe = "eyJhbGciOiJFQ0RILUVTIiwiZW5jIjoiQTI1NkdDTSIsImtpZCI6Im5vLXN1Y2gta2lkIn0..fake.fake.fake";
        assertThat(JWEObject.parse(bogusJwe).getHeader().getKeyID()).isEqualTo("no-such-kid");

        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                null, null, null, null, null, bogusJwe);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> oid4vpService.receiveResponse(request))
                .isInstanceOf(org.omnione.did.base.exception.OpenDidException.class);
    }

    @Test
    @Transactional
    void jweResponse_stateBoundToDifferentSession_isRejected() throws Exception {
        // 세션 A, B 두 개를 만든다. 공개키는 client_metadata로 노출되므로 누구나 A의 키로 암호화할 수 있는데,
        // 페이로드 안의 state에는 B를 가리키는 값을 넣어 세션 혼동을 시도한다 — 거부되어야 한다.
        InitiateRequest initiateRequest = InitiateRequest.builder()
                .policyId(TEST_POLICY_ID)
                .build();
        protocolHandler.initiate(initiateRequest); // 세션 A
        protocolHandler.initiate(initiateRequest); // 세션 B

        List<Oid4vpSession> sessions = sessionRepository.findAll().stream()
                .filter(s -> s.getEncKid() != null)
                .toList();
        assertThat(sessions).hasSizeGreaterThanOrEqualTo(2);
        Oid4vpSession sessionA = sessions.get(sessions.size() - 2);
        Oid4vpSession sessionB = sessions.get(sessions.size() - 1);

        ECKey publicKeyA = ECKey.parse(vpTokenEncryptor.decrypt(sessionA.getEncPrivateKeyJwk())).toPublicJWK();

        // A의 공개키로 암호화하지만 payload의 state는 B의 것 — 복호화는 성공하지만 state 불일치로 거부돼야 한다.
        Map<String, Object> payload = Map.of(
                "vp_token", "{}",
                "presentation_submission", Map.of(),
                "state", sessionB.getState()
        );
        String jweCompact = walletSimulator.encrypt(objectMapper.writeValueAsString(payload), publicKeyA);

        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                null, null, null, null, null, jweCompact);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> oid4vpService.receiveResponse(request))
                .isInstanceOf(org.omnione.did.base.exception.OpenDidException.class);
    }
}
