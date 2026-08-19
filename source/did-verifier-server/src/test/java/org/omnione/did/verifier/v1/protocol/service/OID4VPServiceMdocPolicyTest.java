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

package org.omnione.did.verifier.v1.protocol.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 이 검증자의 mdoc 수용 정책 고정: DID(kid) 기반만 받고 x5chain 기반은 거부한다.
 *
 * <p>x5c 검증 자체는 SDK에 남아 있지만(다른 지갑 상호운용용) 서버는 IACA 신뢰앵커를 운용하지
 * 않으므로, 분류 단계에서 끊어야 SDK 안쪽에서 엉뚱한 사유로 실패하지 않는다.
 */
class OID4VPServiceMdocPolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void x5chainOnlyMdocIsClassifiedAsUnsupported() throws Exception {
        assertTrue(OID4VPService.isX5cBasedMdoc(credential("vp_token_x5chain_only.json")),
                "x5chain만 담긴 mdoc은 거부 대상으로 분류되어야 한다");
    }

    @Test
    void didBasedMdocIsAccepted() throws Exception {
        assertFalse(OID4VPService.isX5cBasedMdoc(credential("vp_token_kid_only.json")));
    }

    /** kid와 x5chain을 모두 담은 mdoc은 kid(DID) 경로를 타므로 거부 대상이 아니다. */
    @Test
    void hybridMdocTakesKidPathAndIsAccepted() throws Exception {
        assertFalse(OID4VPService.isX5cBasedMdoc(credential("vp_token_kid_hybrid.json")));
    }

    private String credential(String fixture) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/mdoc/" + fixture).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(json).get("query_0").get(0).asText();
        }
    }
}
