/*
 * Copyright 2026 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.verifier.v1.protocol.mdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.dto.IdentifierResult;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.MDocVPVerifier;
import org.springframework.core.io.ClassPathResource;

/**
 * DID(kid) 기반 mso_mdoc 검증 경로 스모크 테스트.
 *
 * <p>이 검증자는 DID 기반 mdoc만 지원한다(x5chain 기반은 {@code OID4VPServiceMdocPolicyTest}에서
 * 거부됨을 고정한다). Spring 컨텍스트 없이 SDK 경계(MDocVPVerifier)만 직접 구동한다.
 * 픽스처는 poc-did-mdoc develop(75006c2)에서 가져온 실제 지갑 제출본이다.
 */
class MdocVerificationSmokeTest {

    private static final String KID_ONLY = "fixtures/mdoc/vp_token_kid_only.json";
    private static final String DID_DOC = "fixtures/mdoc/did_omn_issuer.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * kid(DID) mso_mdoc은 DID Document로 해석한 공개키로 IssuerAuth 서명 + IssuerSignedItem digest가
     * 검증된다. 서버는 "DID → 공개키" 해석만 담당하고 검증은 전적으로 SDK가 한다.
     */
    @Test
    void didNativeMdocVerifiesWithResolvedIssuerKey() throws Exception {
        String credential = credential(KID_ONLY);
        MDocVPVerifier verifier = new MDocVPVerifier();

        IdentifierResult issuer = verifier.extractIssuerIdentifier(credential);
        assertEquals(IdentifierResult.Type.MSO_MDOC_KID, issuer.getType());
        assertEquals("did:omn:issuer?versionId=1#assert", issuer.getValue());

        assertTrue(verifier.validateSignature(credential, issuerKeyFromDidDocument(), null),
                "DID Document로 해석한 공개키가 IssuerAuth 서명을 검증해야 한다");
    }

    @Test
    void deviceAuthRejectsMismatchedBinding() throws Exception {
        assertFalse(new MDocVPVerifier().validatePresentationBinding(
                credential(KID_ONLY), "decentralized_identifier:did:omn:other", "other-nonce",
                "https://other.example/oid4vp/response"));
    }

    /**
     * 알려진 구멍 고정: responseUri가 없으면 SessionTranscript를 만들 수 없다는 이유로
     * DeviceAuth를 검증하지 않고 통과시킨다. dc_api 모드에서 presentation binding을 통째로
     * 건너뛰는 것과 같은 결과(홀더 바인딩·재생 방지 없음)다.
     */
    @Test
    void deviceAuthSilentlyPassesWhenResponseUriMissing() throws Exception {
        assertTrue(new MDocVPVerifier().validatePresentationBinding(
                credential(KID_ONLY), "decentralized_identifier:did:omn:other", "other-nonce", null),
                "현재 동작 고정: responseUri가 null이면 검증 없이 통과한다");
    }

    // --- helpers ---------------------------------------------------------------

    private String credential(String fixture) throws Exception {
        return objectMapper.readTree(read(fixture)).get("query_0").get(0).asText();
    }

    private String read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** DID Document의 P-256 JWK(x, y)를 SDK가 받는 uncompressed EC point(base64)로 변환한다. */
    private String issuerKeyFromDidDocument() throws Exception {
        JsonNode jwk = objectMapper.readTree(read(DID_DOC))
                .get("verificationMethod").get(0).get("publicKeyJwk");
        byte[] x = Base64.getUrlDecoder().decode(jwk.get("x").asText());
        byte[] y = Base64.getUrlDecoder().decode(jwk.get("y").asText());

        byte[] point = new byte[1 + x.length + y.length];
        point[0] = 0x04;
        System.arraycopy(x, 0, point, 1, x.length);
        System.arraycopy(y, 0, point, 1 + x.length, y.length);
        return Base64.getEncoder().encodeToString(point);
    }
}
