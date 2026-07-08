package org.omnione.did.verifier.v1.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnione.did.base.db.domain.Oid4vpConfig;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 최종 리뷰 #5 반영: encryption.alg/enc는 SDK의 JweResponseDecryptor가 하드코딩해서 강제하는 값
 * (ECDH-ES/A256GCM)과 다르면 저장 자체를 거부해야 한다 — 그렇지 않으면 Admin에서 다른 값을 저장했을 때
 * 지갑에는 다른 값을 advertise하면서 실제 복호화는 항상 실패하는 조용한 불일치가 생긴다.
 */
class Oid4vpConfigServiceTest {

    private Oid4vpConfigRepository repository;
    private VerifierConfigService verifierConfigService;
    private Oid4vpConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(Oid4vpConfigRepository.class);
        verifierConfigService = mock(VerifierConfigService.class);
        when(repository.findByType("OID4VP")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new Oid4vpConfigService(repository, verifierConfigService, new ObjectMapper());
    }

    private Map<String, Object> baseConfigMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("baseUrl", "http://127.0.0.1:8092");
        config.put("clientName", "OpenDID Verifier");
        return config;
    }

    @Test
    void saveConfig_fixedEncryptionValues_succeeds() {
        Map<String, Object> config = baseConfigMap();
        config.put("encryption", Map.of("alg", "ECDH-ES", "enc", "A256GCM"));

        service.saveConfig(config);

        verify(repository).save(any(Oid4vpConfig.class));
        verify(verifierConfigService).reloadConfig();
    }

    @Test
    void saveConfig_omittedEncryption_usesDefaultsAndSucceeds() {
        // encryption 키 자체가 없으면 OID4VPConfig.Encryption의 Java 기본값(ECDH-ES/A256GCM)이 적용된다.
        Map<String, Object> config = baseConfigMap();

        service.saveConfig(config);

        verify(repository).save(any(Oid4vpConfig.class));
    }

    @Test
    void saveConfig_differentEnc_rejected() {
        Map<String, Object> config = baseConfigMap();
        config.put("encryption", Map.of("alg", "ECDH-ES", "enc", "A128GCM"));

        assertThatThrownBy(() -> service.saveConfig(config))
                .isInstanceOf(OpenDidException.class);

        verify(repository, never()).save(any());
        verify(verifierConfigService, never()).reloadConfig();
    }

    @Test
    void saveConfig_differentAlg_rejected() {
        Map<String, Object> config = baseConfigMap();
        config.put("encryption", Map.of("alg", "ECDH-ES+A256KW", "enc", "A256GCM"));

        assertThatThrownBy(() -> service.saveConfig(config))
                .isInstanceOf(OpenDidException.class);

        verify(repository, never()).save(any());
    }
}
