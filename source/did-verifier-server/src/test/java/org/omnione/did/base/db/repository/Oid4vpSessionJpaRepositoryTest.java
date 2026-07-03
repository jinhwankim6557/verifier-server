package org.omnione.did.base.db.repository;

import org.junit.jupiter.api.Test;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE: {@code @DataJpaTest} auto-detects the nearest {@code @SpringBootConfiguration}
 * (here, {@code VerifierApplication}) and component-scans its entire base package
 * (org.omnione.did). That base package contains several app-wide {@code @Configuration}
 * classes (e.g. OpenApiConfig, SecurityConfig, OpenFeignConfig) with hard dependencies
 * (BuildProperties, a custom ObjectMapper bean, etc.) that the restricted JPA test slice
 * does not provide, so the context fails to load. A minimal nested
 * {@code @SpringBootApplication} scoped only to the oid4vp_session entity/repository
 * (using an include filter, since the repository package also holds several QueryDSL
 * custom-repository impls requiring a JPAQueryFactory bean) avoids pulling in that
 * unrelated configuration, without modifying any production code.
 */
@DataJpaTest
@ContextConfiguration(classes = Oid4vpSessionJpaRepositoryTest.TestApplication.class)
class Oid4vpSessionJpaRepositoryTest {

    @SpringBootApplication
    @EntityScan(basePackageClasses = Oid4vpSession.class)
    @EnableJpaRepositories(
            basePackageClasses = Oid4vpSessionJpaRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Oid4vpSessionJpaRepository.class))
    static class TestApplication {
    }

    @Autowired
    private Oid4vpSessionJpaRepository repository;

    @Test
    void findByEncKid_returnsSessionWithMatchingEncKid() {
        Oid4vpSession session = Oid4vpSession.builder()
                .transactionId("tx-1")
                .state("state-1")
                .status("CREATED")
                .encKid("enc-kid-1")
                .encPrivateKeyJwk("{\"kty\":\"EC\"}")
                .createdAt(System.currentTimeMillis())
                .build();
        repository.save(session);

        Optional<Oid4vpSession> found = repository.findByEncKid("enc-kid-1");

        assertThat(found).isPresent();
        assertThat(found.get().getState()).isEqualTo("state-1");
        assertThat(found.get().getEncPrivateKeyJwk()).isEqualTo("{\"kty\":\"EC\"}");
    }

    @Test
    void findByEncKid_unknownKid_returnsEmpty() {
        assertThat(repository.findByEncKid("no-such-kid")).isEmpty();
    }
}
