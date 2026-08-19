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

package org.omnione.did.verifier.v1.protocol.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upokecenter.cbor.CBORObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * MSO(CBOR) 안의 status list 참조 추출 테스트.
 *
 * <p>파서는 서명을 검증하지 않고 참조만 꺼내므로(진위는 IssuerAuth 검증이 보장) 합성 mdoc으로
 * 구조를 검증한다.
 */
class MdocStatusClaimParserTest {

    private final MdocStatusClaimParser parser = new MdocStatusClaimParser();

    @Test
    @DisplayName("MSO의 status.status_list에서 idx/uri를 추출한다")
    void extractsStatusListReferenceFromMso() {
        String mdoc = mdocWithStatus(
                CBORObject.NewMap().Add("status_list",
                        CBORObject.NewMap().Add("idx", 6).Add("uri", "http://192.168.3.110:8091/status-lists/2")));

        Optional<StatusListRef> ref = parser.parse(mdoc);

        assertThat(ref).isPresent();
        assertThat(ref.get().idx()).isEqualTo(6);
        assertThat(ref.get().uri()).isEqualTo("http://192.168.3.110:8091/status-lists/2");
    }

    @Test
    @DisplayName("status가 없는 MSO는 빈 값을 반환한다 (폐기 확인 생략)")
    void returnsEmptyWhenMsoHasNoStatus() throws Exception {
        assertThat(parser.parse(fixtureCredential())).isEmpty();
    }

    @Test
    @DisplayName("status_list에 idx나 uri가 빠지면 빈 값을 반환한다")
    void returnsEmptyWhenStatusListIsIncomplete() {
        String mdoc = mdocWithStatus(
                CBORObject.NewMap().Add("status_list", CBORObject.NewMap().Add("idx", 6)));

        assertThat(parser.parse(mdoc)).isEmpty();
    }

    @Test
    @DisplayName("mdoc이 아닌 값은 빈 값을 반환한다")
    void returnsEmptyForNonMdocInput() {
        assertThat(parser.parse("not-a-mdoc")).isEmpty();
    }

    // --- helpers ---------------------------------------------------------------

    /** status를 담은 최소 DeviceResponse를 만든다(IssuerAuth 서명은 파서가 보지 않는다). */
    private String mdocWithStatus(CBORObject status) {
        CBORObject mso = CBORObject.NewMap()
                .Add("docType", "eu.europa.ec.eudi.pid.1")
                .Add("version", "1.0")
                .Add("digestAlgorithm", "SHA-256")
                .Add("status", status);

        CBORObject issuerAuth = CBORObject.NewArray();
        issuerAuth.Add(CBORObject.FromObject(new byte[]{(byte) 0xa1, 0x01, 0x26}));  // protected: {1: -7}
        issuerAuth.Add(CBORObject.NewMap());                                          // unprotected
        issuerAuth.Add(CBORObject.FromObject(mso.EncodeToBytes()).WithTag(24));       // payload
        issuerAuth.Add(CBORObject.FromObject(new byte[64]));                          // signature

        CBORObject document = CBORObject.NewMap()
                .Add("docType", "eu.europa.ec.eudi.pid.1")
                .Add("issuerSigned", CBORObject.NewMap()
                        .Add("nameSpaces", CBORObject.NewMap())
                        .Add("issuerAuth", issuerAuth));

        CBORObject deviceResponse = CBORObject.NewMap()
                .Add("version", "1.0")
                .Add("documents", CBORObject.NewArray().Add(document))
                .Add("status", 0);

        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(deviceResponse.EncodeToBytes());
    }

    private String fixtureCredential() throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/mdoc/vp_token_kid_only.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(json).get("query_0").get(0).asText();
        }
    }
}
