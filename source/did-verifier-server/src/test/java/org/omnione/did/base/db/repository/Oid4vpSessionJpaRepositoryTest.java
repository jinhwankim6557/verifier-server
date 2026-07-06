package org.omnione.did.base.db.repository;

import org.junit.jupiter.api.Test;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE: this test previously used {@code @DataJpaTest} with a nested, hand-scoped
 * {@code @SpringBootApplication}/{@code @EnableJpaRepositories(basePackageClasses = ...)}
 * class to avoid pulling in the full {@code VerifierApplication} context (which has
 * app-wide {@code @Configuration} classes with hard dependencies the restricted JPA
 * slice didn't provide). That nested "second root configuration" class was found
 * (during Task 12's investigation) to corrupt Spring Data JPA repository scanning for
 * OTHER unrelated {@code @SpringBootTest}s in this module that don't specify an explicit
 * {@code classes=} (their repository scan silently collapsed down to just
 * {@code Oid4vpSessionJpaRepository}, dropping every other repository bean with
 * {@code NoSuchBeanDefinitionException}) — the exact mechanism wasn't fully pinned down,
 * but removing the nested class from the classpath reliably fixed it every time it was
 * tried, regardless of tweaking the nested class's annotations.
 *
 * Switched to the same {@code @SpringBootTest + @ActiveProfiles("test")} full-context
 * pattern used by the other integration tests in this module (this is now safe because
 * Task 12 also added {@code src/test/resources/import.sql}, which seeds the
 * {@code oid4vp_config} row that {@code VerifierConfigService} needs at
 * {@code @PostConstruct} — previously the reason a full-context boot under the "test"
 * profile could fail on its own, unrelated to OpenApiConfig/SecurityConfig/OpenFeignConfig).
 */
@SpringBootTest
@ActiveProfiles("test")
class Oid4vpSessionJpaRepositoryTest {

    @Autowired
    private Oid4vpSessionJpaRepository repository;

    @Test
    @Transactional
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
    @Transactional
    void findByEncKid_unknownKid_returnsEmpty() {
        assertThat(repository.findByEncKid("no-such-kid")).isEmpty();
    }
}
