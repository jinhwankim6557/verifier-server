package org.omnione.did.verifier.v1.protocol.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.Test;
import org.omnione.did.base.db.domain.Oid4vpSession;
import org.omnione.did.base.db.repository.Oid4vpSessionJpaRepository;
import org.omnione.did.oid4vc.oid4vp.config.OID4VPConfig;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.JweResponseDecryptor;
import org.omnione.did.oid4vc.oid4vp.util.crypto.VPTokenEncryptor;
import org.omnione.did.verifier.v1.protocol.security.Oid4vpEncKeyManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class Oid4vpProtocolHandlerPersistEncKeyTest {

    @Test
    void persistEncKey_updatesExistingSessionRowFoundByRequestId() {
        Oid4vpSessionJpaRepository repo = mock(Oid4vpSessionJpaRepository.class);
        // VerifierConfigService는 구체 클래스라 Mockito로 stub한다(Oid4vpEncKeyManagerTest와 동일 컨벤션).
        // JweResponseDecryptor는 자체 의존성 없는 순수 nimbus 래퍼라 실제 인스턴스를 사용한다.
        VerifierConfigService configService = mock(VerifierConfigService.class);
        OID4VPConfig config = new OID4VPConfig();
        config.getCrypto().setVpTokenEncryptionKey(VPTokenEncryptor.generateKey());
        when(configService.getOID4VPConfig()).thenReturn(config);
        Oid4vpEncKeyManager encKeyManager =
                new Oid4vpEncKeyManager(new ObjectMapper(), new JweResponseDecryptor(), configService,
                        new VPTokenEncryptor(configService));
        Oid4vpSession existing = Oid4vpSession.builder()
                .transactionId("tx-1").state("state-1").status("REQUEST_FETCHED")
                .requestId("req-1").createdAt(1L).build();
        when(repo.findByRequestId("req-1")).thenReturn(Optional.of(existing));

        ECKey keyPair = encKeyManager.generateEphemeralKeyPair();
        Oid4vpProtocolHandler.persistEncKey(repo, encKeyManager, "req-1", keyPair);

        verify(repo).save(argThat(saved ->
                keyPair.getKeyID().equals(saved.getEncKid())
                        && saved.getEncPrivateKeyJwk() != null));
    }
}
