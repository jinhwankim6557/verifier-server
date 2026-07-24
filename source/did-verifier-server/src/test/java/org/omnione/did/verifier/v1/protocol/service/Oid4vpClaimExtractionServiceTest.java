package org.omnione.did.verifier.v1.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.omnione.did.verifier.v1.protocol.api.dto.ClaimView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Oid4vpClaimExtractionServiceTest {

    // did-oid4vp-sdk-server의 SDJWTCredentialAdapterFormatTest에서 가져온 실제 제출값
    // (typ=dc+sd-jwt-did, cnf.jwk, disclosure 1개: date_of_issuance="")
    private static final String REAL_DC_SD_JWT_DID_CREDENTIAL =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpvbW46aXNzdWVyP3ZlcnNpb25JZD0xI2Fzc2VydCIsInR5cCI6ImRjK3NkLWp3dC1kaWQifQ"
                    + ".eyJpc3MiOiJkaWQ6b21uOmlzc3VlciIsImlhdCI6MTc4MzkwNjg5NCwiZXhwIjoxODE1NDQyODk0LCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsImNuZiI6eyJqd2siOnsia3R5IjoiRUMiLCJ1c2UiOiJzaWciLCJjcnYiOiJQLTI1NiIsIngiOiJCSnA4Si1QSzVnN3F3a3lwWS1kWEVQSGd2TGJvWndlNWtSX2EwQ0tkazl3IiwieSI6IlBnRWZIV0hXdzFOd0wtT2lyREk3a1lUVUVyWHJNX3RLZHBON2ZQU2ZYVG8ifX0sIl9zZCI6WyJrbXlTbVZjOFJ6UzJGc2FFRnlKQUxrc1ZaTFkyR0FjNENSdmh4NVlSYWJnIl19"
                    + ".8QcbKfKViLDAEV1Jce07cg62Eml9UauMkO7hCYD2lS1uH49FLgFOKy1V64wFnTGXmz9PoQxnLxszBmjaugY74Q"
                    + "~WyJyUzBqVEVYWExSbDlVUmJXekNvQTRnIiwiZGF0ZV9vZl9pc3N1YW5jZSIsIiJd"
                    + "~eyJhbGciOiJFUzI1NiIsInR5cCI6ImtiK2p3dCJ9.eyJhdWQiOiJkZWNlbnRyYWxpemVkX2lkZW50aWZpZXI6ZGlkOm9tbjp2ZXJpZmllciIsIm5vbmNlIjoiYzJiNzhlZDMtZGUyZC00MDc3LTk5ZDMtZGY0OTU4NGYyODM3IiwiaWF0IjoxNzg0MDgwNDIxLCJzZF9oYXNoIjoiV0JqY19GazBsanlfcTBjZzVtLVIyWmVaQzA2TFdYbHJ2bEMzQlRUZFdUSSJ9.Aea3fPRjBoFQyVRPIe1JjQaOxkYZgbkyrzA9WpnVzW0pytlh9SeYxtFl2QUwNMVj4tZ6FwqOKhRHm7KNKO3E_w";

    private final Oid4vpClaimExtractionService service =
            new Oid4vpClaimExtractionService(null, new ObjectMapper());

    @Test
    void extractsDisclosedClaimFromRealDcSdJwtDidCredential() {
        List<ClaimView> claims = service.extractFromCredential(REAL_DC_SD_JWT_DID_CREDENTIAL);

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getCaption()).isEqualTo("date_of_issuance");
        assertThat(claims.get(0).getValue()).isEqualTo("");
    }

    @Test
    void returnsEmptyListForUnrecognizedCredentialString() {
        assertThat(service.extractFromCredential("not-a-credential")).isEmpty();
    }

    @Test
    void returnsEmptyListForBlankCredential() {
        assertThat(service.extractFromCredential("")).isEmpty();
    }
}
