package org.omnione.did.oid4vc.oid4vp.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ResponseModeWhitelistTest {

    @Test
    void oid4vpHelperService_directPostJwt_isRecognizedAsRequiringResponseUri() {
        assertThat(OID4VPHelperService.requiresResponseUri("direct_post.jwt")).isTrue();
    }

    @Test
    void oid4vpHelperService_constant_hasExpectedValue() {
        assertThat(OID4VPHelperService.RESPONSE_MODE_DIRECT_POST_JWT).isEqualTo("direct_post.jwt");
    }
}
