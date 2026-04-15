package org.omnione.did.verifier.v1.oid4vp.config;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.repository.DcqlScopeMappingRepository;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.base.db.repository.Oid4vpSessionJpaRepository;
import org.omnione.did.oid4vc.formatter.exception.FormatterException;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.VPTokenVerifier;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.OpenDIDVPVerifier;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.SDJWTVPVerifier;
import org.omnione.did.oid4vc.oid4vp.repository.DCQLScopeMappingRepository;
import org.omnione.did.oid4vc.oid4vp.repository.OID4VPRepository;
import org.omnione.did.oid4vc.oid4vp.repository.SessionRepository;
import org.omnione.did.verifier.v1.oid4vp.adapter.JpaDcqlScopeMappingRepositoryAdapter;
import org.omnione.did.verifier.v1.oid4vp.adapter.JpaOid4vpRepositoryAdapter;
import org.omnione.did.verifier.v1.oid4vp.adapter.JpaSessionRepositoryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SDK Repository 인터페이스에 JPA 기반 Adapter를 Bean으로 등록한다.
 * SDK의 @ConditionalOnMissingBean에 의해 InMemory 구현체 대신 이 Adapter가 사용된다.
 */

@Slf4j
@Configuration
public class OID4VPIntegrationConfig {

    @Bean
    public OID4VPRepository oid4vpRepository(Oid4vpConfigRepository jpaRepository) {
        return new JpaOid4vpRepositoryAdapter(jpaRepository);
    }

    @Bean
    public DCQLScopeMappingRepository sdkDcqlScopeMappingRepository(DcqlScopeMappingRepository jpaRepository) {
        return new JpaDcqlScopeMappingRepositoryAdapter(jpaRepository);
    }

    @Bean
    public SessionRepository sdkSessionRepository(Oid4vpSessionJpaRepository jpaRepository) {
        return new JpaSessionRepositoryAdapter(jpaRepository);
    }

    @Bean
    public VPTokenVerifier sdJwtVPVerifier() {
        return new SDJWTVPVerifier();
    }

    /**
     * 개발/테스트용: 서명 검증을 건너뜁니다.
     * Mock wallet처럼 실제 서명이 없는 환경에서만 사용하세요.
     *
     * 활성화: application.yml에 oid4vp.dev.skip-signature-verification: true 설정
     * 비활성화(기본): 미설정 또는 false → 아래 실제 검증 Bean이 사용됨
     */
    @Bean
    @ConditionalOnProperty(name = "oid4vp.dev.skip-signature-verification", havingValue = "true")
    public VPTokenVerifier openDidVPVerifierSkip() {
        log.warn("=====================================================");
        log.warn("  [DEV] OpenDID VP 서명 검증이 비활성화되어 있습니다.");
        log.warn("  운영 환경에서는 반드시 비활성화하세요.");
        log.warn("  (oid4vp.dev.skip-signature-verification=false)");
        log.warn("=====================================================");
        return new OpenDIDVPVerifier() {
            @Override
            public boolean validateSignature(String credential, String issuerPublicKey, String holderPublicKey)
                    throws FormatterException {
                log.warn("[DEV] VP 서명 검증 건너뜀 (mock 모드)");
                return true;
            }
        };
    }

    /**
     * 운영용: 실제 서명 검증을 수행합니다.
     * OID4VPService에서 DID Document를 통해 해석한 실제 공개키가 전달되어야 합니다.
     */
    @Bean
    @ConditionalOnProperty(
        name = "oid4vp.dev.skip-signature-verification",
        havingValue = "false",
        matchIfMissing = true
    )
    public VPTokenVerifier openDidVPVerifier() {
        return new OpenDIDVPVerifier();
    }
}
