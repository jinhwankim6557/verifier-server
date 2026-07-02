package org.omnione.did.verifier.v1.protocol.service.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class StatusClaimParserTest {

    private StatusClaimParser parser;

    @BeforeEach
    void setUp() {
        parser = new StatusClaimParser();
    }

    private String buildSdJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"typ\":\"vc+sd-jwt\",\"alg\":\"ES256\",\"kid\":\"did:omn:issuer#assert\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".fakesig~disc1~";
    }

    @Test
    @DisplayName("status claim 있으면 StatusListRef 반환")
    void parse_returns_ref_when_status_present() {
        String sdJwt = buildSdJwt("""
            {"iss":"did:omn:issuer","status":{"status_list":{"idx":42,"uri":"https://issuer.example.com/statuslists/1"}}}
            """);

        Optional<StatusListRef> ref = parser.parse(sdJwt);

        assertThat(ref).isPresent();
        assertThat(ref.get().idx()).isEqualTo(42);
        assertThat(ref.get().uri()).isEqualTo("https://issuer.example.com/statuslists/1");
    }

    @Test
    @DisplayName("status claim 없으면 Optional.empty()")
    void parse_returns_empty_when_no_status() {
        String sdJwt = buildSdJwt("{\"iss\":\"did:omn:issuer\",\"sub\":\"did:omn:holder\"}");

        Optional<StatusListRef> ref = parser.parse(sdJwt);

        assertThat(ref).isEmpty();
    }

    @Test
    @DisplayName("status_list 없이 status만 있어도 Optional.empty()")
    void parse_returns_empty_when_status_list_missing() {
        String sdJwt = buildSdJwt("{\"iss\":\"did:omn:issuer\",\"status\":{}}");

        Optional<StatusListRef> ref = parser.parse(sdJwt);

        assertThat(ref).isEmpty();
    }

    @Test
    @DisplayName("SD-JWT 파싱 오류 시 Optional.empty() (예외 전파 안 함)")
    void parse_returns_empty_on_malformed_jwt() {
        Optional<StatusListRef> ref = parser.parse("not.a.valid.sdjwt");

        assertThat(ref).isEmpty();
    }
}
