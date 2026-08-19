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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.dcql.core.credential.impl.MDocCredentialAdapter;
import org.springframework.core.io.ClassPathResource;

/**
 * DCQL 포맷 식별자 판별 테스트.
 *
 * <p>SD-JWT와 마찬가지로 DCQL의 {@code format}은 credential 내용으로 결정된다. mdoc은
 * IssuerAuth에 kid(DID)가 있으면 {@code mso_mdoc-did}, x5chain으로만 발급자를 식별하면
 * {@code mso_mdoc}이다. 이 값이 DCQL에 적힌 format과 일치해야 검증을 통과한다.
 */
class MdocFormatResolutionTest {

    private final MDocCredentialAdapter adapter = new MDocCredentialAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("kid(DID)를 가진 mdoc은 mso_mdoc-did로 식별된다")
    void didNativeMdocResolvesToMsoMdocDid() throws Exception {
        assertEquals("mso_mdoc-did", adapter.parse(credential("vp_token_kid_only.json")).getFormat());
    }

    @Test
    @DisplayName("x5chain만 가진 mdoc은 mso_mdoc으로 식별된다")
    void x5chainMdocResolvesToMsoMdoc() throws Exception {
        assertEquals("mso_mdoc", adapter.parse(credential("vp_token_x5chain_only.json")).getFormat());
    }

    @Test
    @DisplayName("kid와 x5chain을 모두 가지면 kid가 우선한다")
    void hybridMdocResolvesToMsoMdocDid() throws Exception {
        assertEquals("mso_mdoc-did", adapter.parse(credential("vp_token_kid_hybrid.json")).getFormat());
    }

    @Test
    @DisplayName("두 포맷 식별자 모두 어댑터가 처리한다")
    void adapterSupportsBothFormats() {
        assertTrue(adapter.supports("mso_mdoc"));
        assertTrue(adapter.supports("mso_mdoc-did"));
    }

    private String credential(String fixture) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/mdoc/" + fixture).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(json).get("query_0").get(0).asText();
        }
    }
}
