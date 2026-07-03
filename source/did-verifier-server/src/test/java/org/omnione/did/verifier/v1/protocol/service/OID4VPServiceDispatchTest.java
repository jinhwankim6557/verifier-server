package org.omnione.did.verifier.v1.protocol.service;

import org.junit.jupiter.api.Test;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;

import static org.assertj.core.api.Assertions.assertThat;

class OID4VPServiceDispatchTest {

    @Test
    void isEncrypted_returnsTrueWhenResponseFieldPresent() {
        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                null, null, null, null, null, "eyJhbGciOiJFQ0RILUVTIn0...");
        assertThat(OID4VPService.isEncrypted(request)).isTrue();
    }

    @Test
    void isEncrypted_returnsFalseWhenResponseFieldBlank() {
        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                "vp-token", "state-1", "{}", null, null, null);
        assertThat(OID4VPService.isEncrypted(request)).isFalse();

        Oid4vpResponseRequest blank = new Oid4vpResponseRequest(
                "vp-token", "state-1", "{}", null, null, "   ");
        assertThat(OID4VPService.isEncrypted(blank)).isFalse();
    }
}
