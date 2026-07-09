package org.omnione.did.verifier.v1.protocol.service.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.util.BaseCoreDidUtil;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.did.VerificationMethod;
import org.omnione.did.verifier.v1.agent.service.DidDocService;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * kid에 versionId 쿼리(?)가 섞인 경우의 DID 파싱, 서명/만료/sub 검증 실패 경로를 다룬다.
 * BaseCoreDidUtil.getVerificationMethod는 did-core-sdk(prebuilt jar)의 DidManager를 거치므로
 * 여기서는 mockStatic으로 우회하고, 실제 서명/공개키 압축 해제 로직(ECDSAVerifier, MultibaseUtils,
 * BouncyCastle 디코딩)은 실제 키로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class StatusListTokenVerifierTest {

    static {
        // StatusListTokenVerifier.decodeP256PublicKey()가 KeyFactory.getInstance("EC", "BC")를 쓰므로
        // "BC" provider가 등록돼 있어야 한다 (CanonicalMessageDebugTest와 동일 패턴).
        Security.addProvider(new BouncyCastleProvider());
    }

    @Mock
    private DidDocService didDocService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StatusListTokenVerifier verifier;
    private KeyPair keyPair;

    private static final String URI = "https://example.com/statuslists/1";

    @BeforeEach
    void setUp() throws Exception {
        verifier = new StatusListTokenVerifier(didDocService, objectMapper);
        keyPair = generateP256KeyPair();
    }

    @Test
    @DisplayName("kid에 ?versionId=가 있어도 DID를 올바르게 잘라내고 정상 검증한다")
    void valid_token_with_versioned_kid_verifies_and_parses_did_correctly() throws Exception {
        String kid = "did:omn:issuer?versionId=1#assert";
        String jwt = buildSignedToken(kid, URI, futureExp(), (ECPrivateKey) keyPair.getPrivate());

        try (MockedStatic<BaseCoreDidUtil> mocked = mockStatic(BaseCoreDidUtil.class)) {
            VerificationMethod vm = new VerificationMethod();
            vm.setPublicKeyMultibase(toMultibase((ECPublicKey) keyPair.getPublic()));
            mocked.when(() -> BaseCoreDidUtil.getVerificationMethod(any(DidDocument.class), eq("assert")))
                    .thenReturn(vm);
            when(didDocService.getDidDocument("did:omn:issuer")).thenReturn(mock(DidDocument.class));

            StatusListTokenPayload payload = verifier.verify(jwt, URI);

            assertThat(payload.bits()).isEqualTo(1);
            assertThat(payload.lst()).isEqualTo("AAA");
            // 핵심 회귀 검증: '?versionId=1'이 DID에 섞여 들어가지 않고 정확히 잘렸는지 확인
            verify(didDocService).getDidDocument("did:omn:issuer");
        }
    }

    @Test
    @DisplayName("등록된 공개키와 다른 키로 서명하면 STATUS_LIST_TOKEN_INVALID 예외")
    void wrong_signing_key_throws() throws Exception {
        String kid = "did:omn:issuer#assert";
        String jwt = buildSignedToken(kid, URI, futureExp(), (ECPrivateKey) keyPair.getPrivate());
        KeyPair otherKeyPair = generateP256KeyPair();

        try (MockedStatic<BaseCoreDidUtil> mocked = mockStatic(BaseCoreDidUtil.class)) {
            VerificationMethod vm = new VerificationMethod();
            vm.setPublicKeyMultibase(toMultibase((ECPublicKey) otherKeyPair.getPublic()));
            mocked.when(() -> BaseCoreDidUtil.getVerificationMethod(any(DidDocument.class), eq("assert")))
                    .thenReturn(vm);
            when(didDocService.getDidDocument("did:omn:issuer")).thenReturn(mock(DidDocument.class));

            assertThatThrownBy(() -> verifier.verify(jwt, URI))
                    .isInstanceOf(OpenDidException.class)
                    .extracting(e -> ((OpenDidException) e).getErrorCode())
                    .isEqualTo(org.omnione.did.base.exception.ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }

    @Test
    @DisplayName("sub가 요청한 uri와 다르면 예외")
    void sub_mismatch_throws() throws Exception {
        String kid = "did:omn:issuer#assert";
        String jwt = buildSignedToken(kid, "https://example.com/statuslists/OTHER", futureExp(), (ECPrivateKey) keyPair.getPrivate());

        try (MockedStatic<BaseCoreDidUtil> mocked = mockStatic(BaseCoreDidUtil.class)) {
            VerificationMethod vm = new VerificationMethod();
            vm.setPublicKeyMultibase(toMultibase((ECPublicKey) keyPair.getPublic()));
            mocked.when(() -> BaseCoreDidUtil.getVerificationMethod(any(DidDocument.class), eq("assert")))
                    .thenReturn(vm);
            when(didDocService.getDidDocument("did:omn:issuer")).thenReturn(mock(DidDocument.class));

            assertThatThrownBy(() -> verifier.verify(jwt, URI))
                    .isInstanceOf(OpenDidException.class)
                    .extracting(e -> ((OpenDidException) e).getErrorCode())
                    .isEqualTo(org.omnione.did.base.exception.ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }

    @Test
    @DisplayName("만료된 토큰이면 예외")
    void expired_token_throws() throws Exception {
        String kid = "did:omn:issuer#assert";
        long pastExp = Instant.now().getEpochSecond() - 3600;
        String jwt = buildSignedToken(kid, URI, pastExp, (ECPrivateKey) keyPair.getPrivate());

        try (MockedStatic<BaseCoreDidUtil> mocked = mockStatic(BaseCoreDidUtil.class)) {
            VerificationMethod vm = new VerificationMethod();
            vm.setPublicKeyMultibase(toMultibase((ECPublicKey) keyPair.getPublic()));
            mocked.when(() -> BaseCoreDidUtil.getVerificationMethod(any(DidDocument.class), eq("assert")))
                    .thenReturn(vm);
            when(didDocService.getDidDocument("did:omn:issuer")).thenReturn(mock(DidDocument.class));

            assertThatThrownBy(() -> verifier.verify(jwt, URI))
                    .isInstanceOf(OpenDidException.class)
                    .extracting(e -> ((OpenDidException) e).getErrorCode())
                    .isEqualTo(org.omnione.did.base.exception.ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }

    private long futureExp() {
        return Instant.now().getEpochSecond() + 3600;
    }

    private String buildSignedToken(String kid, String sub, long exp, ECPrivateKey signingKey) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(kid).build();

        Map<String, Object> statusList = new LinkedHashMap<>();
        statusList.put("bits", 1);
        statusList.put("lst", "AAA");

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("sub", sub);
        payloadMap.put("exp", exp);
        payloadMap.put("ttl", 3600);
        payloadMap.put("status_list", statusList);

        JWSObject jwsObject = new JWSObject(header, new Payload(objectMapper.writeValueAsString(payloadMap)));
        jwsObject.sign(new ECDSASigner(signingKey));
        return jwsObject.serialize();
    }

    private KeyPair generateP256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    // compressed SEC1 형식(33바이트) + multibase 'm' prefix(Base64) 인코딩.
    // StatusListTokenVerifier.decodeP256PublicKey()가 기대하는 정확히 그 형식.
    private String toMultibase(ECPublicKey publicKey) {
        ECPoint point = publicKey.getW();
        byte[] x = toFixedLength(point.getAffineX(), 32);
        boolean yIsEven = !point.getAffineY().testBit(0);

        byte[] compressed = new byte[33];
        compressed[0] = (byte) (yIsEven ? 0x02 : 0x03);
        System.arraycopy(x, 0, compressed, 1, 32);

        return "m" + Base64.getEncoder().encodeToString(compressed);
    }

    private byte[] toFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) return bytes;
        byte[] result = new byte[length];
        if (bytes.length > length) {
            System.arraycopy(bytes, bytes.length - length, result, 0, length);
        } else {
            System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        }
        return result;
    }
}
