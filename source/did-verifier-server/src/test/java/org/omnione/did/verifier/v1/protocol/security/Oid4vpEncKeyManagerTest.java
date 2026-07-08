package org.omnione.did.verifier.v1.protocol.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.config.OID4VPConfig;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.JweResponseDecryptor;
import org.omnione.did.oid4vc.oid4vp.util.crypto.VPTokenEncryptor;

import java.security.interfaces.ECPrivateKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Oid4vpEncKeyManagerTest {

    private Oid4vpEncKeyManager manager;

    @BeforeEach
    void setUp() {
        // OID4VPConfig.Encryption 기본값 = ECDH-ES/A256GCM(Task 2 Step 6). DB 접근 없이 순수 단위 테스트하려고
        // VerifierConfigService는 Mockito로 stub한다(구체 클래스라 수동 mock 상속보다 짧음 — Task 7과 동일 컨벤션).
        // JweResponseDecryptor는 자체 의존성이 없는 순수 nimbus 래퍼(DB 접근 없음)이므로 mock 대신 실제 인스턴스를 사용한다.
        // VPTokenEncryptor도 실제 인스턴스 사용(순수 AES-256-GCM 래퍼) — enc private key at-rest 암호화 검증 목적.
        VerifierConfigService configService = mock(VerifierConfigService.class);
        OID4VPConfig config = new OID4VPConfig();
        config.getCrypto().setVpTokenEncryptionKey(VPTokenEncryptor.generateKey());
        when(configService.getOID4VPConfig()).thenReturn(config);
        VPTokenEncryptor vpTokenEncryptor = new VPTokenEncryptor(configService);
        manager = new Oid4vpEncKeyManager(new ObjectMapper(), new JweResponseDecryptor(), configService, vpTokenEncryptor);
    }

    @Test
    void generateEphemeralKeyPair_producesEcP256KeyWithEncUse() {
        ECKey key = manager.generateEphemeralKeyPair();

        assertThat(key.getCurve().getName()).isEqualTo("P-256");
        assertThat(key.getKeyUse().getValue()).isEqualTo("enc");
        assertThat(key.getAlgorithm().getName()).isEqualTo("ECDH-ES");
        assertThat(key.getKeyID()).isNotBlank();
        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    void buildClientMetadataJson_containsPublicJwkOnly() throws Exception {
        ECKey key = manager.generateEphemeralKeyPair();

        String json = manager.buildClientMetadataJson(key);

        ObjectMapper om = new ObjectMapper();
        Map<?, ?> parsed = om.readValue(json, Map.class);
        Map<?, ?> jwks = (Map<?, ?>) parsed.get("jwks");
        assertThat(jwks).isNotNull();
        assertThat((java.util.List<?>) jwks.get("keys")).hasSize(1);
        Map<?, ?> jwk = (Map<?, ?>) ((java.util.List<?>) jwks.get("keys")).get(0);
        assertThat(jwk.get("kty")).isEqualTo("EC");
        assertThat(jwk.get("d")).isNull(); // 공개키만 노출, 개인키 없음
        assertThat((java.util.List<Object>) parsed.get("encrypted_response_enc_values_supported"))
                .containsExactly("A256GCM"); // OID4VPConfig.Encryption 기본값에서 로드됨(하드코딩 아님)
    }

    @Test
    void toStorableJwk_thenLoadPrivateKey_roundTrips() throws Exception {
        ECKey key = manager.generateEphemeralKeyPair();
        String stored = manager.toStorableJwk(key);

        ECPrivateKey loaded = manager.loadPrivateKey(stored);

        assertThat(loaded).isEqualTo(key.toECPrivateKey());
    }

    @Test
    void toStorableJwk_encryptsAtRest_notPlaintextJwk() {
        ECKey key = manager.generateEphemeralKeyPair();

        String stored = manager.toStorableJwk(key);

        // 최종 리뷰 지적: 개인키(d 포함)가 평문으로 저장되면 안 된다 — VPTokenEncryptor로 암호화된 값이어야
        // 하므로 JWK JSON으로 그대로 파싱되지 않아야 한다(평문이라면 파싱이 성공해버릴 것).
        assertThat(stored).doesNotContain("\"d\"").doesNotContain("\"kty\"");
        assertThatThrownBy(() -> ECKey.parse(stored)).isInstanceOf(Exception.class);
    }

    @Test
    void extractKid_readsKidFromJweHeaderWithoutPrivateKey() throws Exception {
        ECKey key = manager.generateEphemeralKeyPair();
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID(key.getKeyID())
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(key.toPublicJWK()));

        assertThat(manager.extractKid(jweObject.serialize())).isEqualTo(key.getKeyID());
    }

    @Test
    void loadPrivateKey_nullStoredJwk_throwsOpenDidException() {
        assertThatThrownBy(() -> manager.loadPrivateKey(null))
                .isInstanceOf(OpenDidException.class);
    }

    @Test
    void extractKid_headerWithoutKid_throwsInsteadOfReturningNull() throws Exception {
        // 최종 리뷰 지적: kid가 없는 JWE는 null을 그대로 반환하면 findByEncKid(null)이
        // enc_kid IS NULL인 세션에 잘못 매칭되거나 예외를 던질 수 있다 — 여기서 명확히 걸러야 한다.
        ECKey key = manager.generateEphemeralKeyPair();
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(key.toPublicJWK()));

        assertThatThrownBy(() -> manager.extractKid(jweObject.serialize()))
                .isInstanceOf(OpenDidException.class);
    }
}
