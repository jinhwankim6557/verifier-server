package org.omnione.did.verifier.v1.protocol.util;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;

/**
 * OID4VP JWE 응답 암호화 테스트 하니스(Wallet 시뮬레이터).
 * 실제 Wallet 구현 합의 전, Verifier 측 JWE 복호화·검증 경로를 단독으로 검증하기 위한 테스트 전용 유틸이다.
 */
public class TestJweEncryptor {

    public String encrypt(String plaintextJson, ECKey recipientPublicJwk) throws Exception {
        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID(recipientPublicJwk.getKeyID())
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload(plaintextJson));
        jweObject.encrypt(new ECDHEncrypter(recipientPublicJwk));
        return jweObject.serialize();
    }
}
