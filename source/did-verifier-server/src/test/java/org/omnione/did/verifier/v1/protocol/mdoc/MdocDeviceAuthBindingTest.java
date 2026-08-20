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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.formatter.oid4vp.verifier.impl.MDocVPVerifier;
import org.springframework.core.io.ClassPathResource;

/**
 * 암호화 응답(direct_post.jwt) 세션에서의 mdoc DeviceAuth 바인딩 회귀 테스트.
 *
 * <p>OpenID4VPHandoverInfo의 세 번째 요소는 응답을 암호화할 때 Verifier 암호화 공개키의
 * SHA-256 JWK Thumbprint(RFC 7638)이고, 평문 응답일 때만 null이다. 지갑은 이 값을 넣어
 * DeviceAuth에 서명하므로, 검증 측이 null로 계산하면 credential이 멀쩡해도 서명이 깨진다.
 *
 * <p>픽스처는 2026-08-20 실제 지갑 제출본이다.
 */
class MdocDeviceAuthBindingTest {

    private static final String FIXTURE = "fixtures/mdoc/vp_token_encrypted_session.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("암호화 세션: 올바른 thumbprint를 주면 DeviceAuth가 검증된다")
    void deviceAuthVerifiesWithEncryptionKeyThumbprint() throws Exception {
        JsonNode f = fixture();

        assertTrue(new MDocVPVerifier().validatePresentationBinding(
                credential(f),
                f.get("clientId").asText(),
                f.get("nonce").asText(),
                f.get("responseUri").asText(),
                thumbprint(f.get("responseEncryptionJwk"))));
    }

    /** thumbprint 자리를 null로 두면(수정 전 동작) 같은 credential이 거부된다. */
    @Test
    @DisplayName("암호화 세션인데 thumbprint를 빠뜨리면 DeviceAuth가 실패한다")
    void deviceAuthFailsWhenThumbprintIsOmitted() throws Exception {
        JsonNode f = fixture();

        assertFalse(new MDocVPVerifier().validatePresentationBinding(
                credential(f),
                f.get("clientId").asText(),
                f.get("nonce").asText(),
                f.get("responseUri").asText(),
                null));
    }

    // --- helpers ---------------------------------------------------------------

    private JsonNode fixture() throws Exception {
        try (InputStream in = new ClassPathResource(FIXTURE).getInputStream()) {
            return objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private String credential(JsonNode fixture) {
        return fixture.get("vpToken").get("pid_mdoc").get(0).asText();
    }

    /** RFC 7638: 필수 멤버만 사전순으로, 공백 없이 직렬화한 뒤 SHA-256. */
    private byte[] thumbprint(JsonNode jwk) throws Exception {
        String canonical = "{\"crv\":\"" + jwk.get("crv").asText()
                + "\",\"kty\":\"EC\",\"x\":\"" + jwk.get("x").asText()
                + "\",\"y\":\"" + jwk.get("y").asText() + "\"}";
        return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
