package org.omnione.did.oid4vc.oid4vp.util.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPException;

import java.security.interfaces.ECPrivateKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JweResponseDecryptorTest {

    private final JweResponseDecryptor decryptor = new JweResponseDecryptor();

    @Test
    void decrypt_roundTrip_returnsOriginalPlaintextAndHeader() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("test-kid-1").generate();
        String plaintext = "{\"vp_token\":\"abc\",\"state\":\"s-1\",\"presentation_submission\":{}}";

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID("test-kid-1")
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload(plaintext));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        JweResponseDecryptor.DecryptedResponse result =
                decryptor.decrypt(jweCompact, recipientKey.toECPrivateKey());

        assertThat(result.getPlaintext()).isEqualTo(plaintext);
        assertThat(result.getProtectedHeader().getKeyID()).isEqualTo("test-kid-1");
    }

    @Test
    void decrypt_wrongPrivateKey_throwsOID4VPException() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-a").generate();
        ECKey wrongKey = new ECKeyGenerator(Curve.P_256).keyID("kid-b").generate();

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID("kid-a").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        ECPrivateKey wrongPrivateKey = wrongKey.toECPrivateKey();

        assertThatThrownBy(() -> decryptor.decrypt(jweCompact, wrongPrivateKey))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void decrypt_malformedCompact_throwsOID4VPException() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("k").generate();
        assertThatThrownBy(() -> decryptor.decrypt("not-a-jwe", key.toECPrivateKey()))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void parseHeader_returnsKidWithoutDecrypting() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-header-only").generate();
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID("kid-header-only").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));

        assertThat(decryptor.parseHeader(jweObject.serialize()).getKeyID()).isEqualTo("kid-header-only");
    }

    @Test
    void decrypt_keyWrapAlgorithmVariant_throwsOID4VPException() throws Exception {
        // Off-spec: ECDH-ES+A256KW is a valid ECDH-ES family alg that nimbus's ECDHDecrypter
        // would happily accept, but this project fixes alg to ECDH-ES (direct agreement) only.
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-kw").generate();

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM)
                .keyID("kid-kw").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        assertThatThrownBy(() -> decryptor.decrypt(jweCompact, recipientKey.toECPrivateKey()))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void decrypt_nonA256GcmEncryptionMethod_throwsOID4VPException() throws Exception {
        // Off-spec: alg is the fixed ECDH-ES, but enc is A128GCM instead of the fixed A256GCM.
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-enc").generate();

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
                .keyID("kid-enc").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        assertThatThrownBy(() -> decryptor.decrypt(jweCompact, recipientKey.toECPrivateKey()))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsOID4VPException() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-tamper").generate();

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID("kid-tamper").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{\"vp_token\":\"abc\"}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        // Compact JWE = header.encryptedKey.iv.ciphertext.tag - flip a char in the ciphertext part.
        String[] parts = jweCompact.split("\\.", -1);
        char[] ciphertextChars = parts[3].toCharArray();
        ciphertextChars[0] = ciphertextChars[0] == 'A' ? 'B' : 'A';
        parts[3] = new String(ciphertextChars);
        String tamperedCompact = String.join(".", parts);

        ECPrivateKey privateKey = recipientKey.toECPrivateKey();
        assertThatThrownBy(() -> decryptor.decrypt(tamperedCompact, privateKey))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void parseHeader_nullInput_throwsOID4VPException() {
        assertThatThrownBy(() -> decryptor.parseHeader(null))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void decrypt_nullCompact_throwsOID4VPException() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-null-compact").generate();
        ECPrivateKey privateKey = recipientKey.toECPrivateKey();

        assertThatThrownBy(() -> decryptor.decrypt(null, privateKey))
                .isInstanceOf(OID4VPException.class);
    }

    @Test
    void decrypt_nullPrivateKey_throwsOID4VPException() throws Exception {
        ECKey recipientKey = new ECKeyGenerator(Curve.P_256).keyID("kid-null-key").generate();
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID("kid-null-key").build();
        JWEObject jweObject = new JWEObject(header, new Payload("{}"));
        jweObject.encrypt(new ECDHEncrypter(recipientKey.toPublicJWK()));
        String jweCompact = jweObject.serialize();

        assertThatThrownBy(() -> decryptor.decrypt(jweCompact, null))
                .isInstanceOf(OID4VPException.class);
    }
}
