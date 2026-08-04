package org.omnione.did.oid4vc.dcql.core.credential.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;

class SDJWTCredentialAdapterFormatTest {

    // Real submitted vp_token credential (NationalID), header typ="dc+sd-jwt-did", cnf.jwk (not cnf.kid).
    private static final String REAL_DC_SD_JWT_DID_CREDENTIAL =
        "eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpvbW46aXNzdWVyP3ZlcnNpb25JZD0xI2Fzc2VydCIsInR5cCI6ImRjK3NkLWp3dC1kaWQifQ"
            + ".eyJpc3MiOiJkaWQ6b21uOmlzc3VlciIsImlhdCI6MTc4MzkwNjg5NCwiZXhwIjoxODE1NDQyODk0LCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsImNuZiI6eyJqd2siOnsia3R5IjoiRUMiLCJ1c2UiOiJzaWciLCJjcnYiOiJQLTI1NiIsIngiOiJCSnA4Si1QSzVnN3F3a3lwWS1kWEVQSGd2TGJvWndlNWtSX2EwQ0tkazl3IiwieSI6IlBnRWZIV0hXdzFOd0wtT2lyREk3a1lUVUVyWHJNX3RLZHBON2ZQU2ZYVG8ifX0sIl9zZCI6WyJrbXlTbVZjOFJ6UzJGc2FFRnlKQUxrc1ZaTFkyR0FjNENSdmh4NVlSYWJnIl19"
            + ".8QcbKfKViLDAEV1Jce07cg62Eml9UauMkO7hCYD2lS1uH49FLgFOKy1V64wFnTGXmz9PoQxnLxszBmjaugY74Q"
            + "~WyJyUzBqVEVYWExSbDlVUmJXekNvQTRnIiwiZGF0ZV9vZl9pc3N1YW5jZSIsIiJd"
            + "~eyJhbGciOiJFUzI1NiIsInR5cCI6ImtiK2p3dCJ9.eyJhdWQiOiJkZWNlbnRyYWxpemVkX2lkZW50aWZpZXI6ZGlkOm9tbjp2ZXJpZmllciIsIm5vbmNlIjoiYzJiNzhlZDMtZGUyZC00MDc3LTk5ZDMtZGY0OTU4NGYyODM3IiwiaWF0IjoxNzg0MDgwNDIxLCJzZF9oYXNoIjoiV0JqY19GazBsanlfcTBjZzVtLVIyWmVaQzA2TFdYbHJ2bEMzQlRUZFdUSSJ9.Aea3fPRjBoFQyVRPIe1JjQaOxkYZgbkyrzA9WpnVzW0pytlh9SeYxtFl2QUwNMVj4tZ6FwqOKhRHm7KNKO3E_w";

    @Test
    void realDcSdJwtDidCredential_isClassifiedByTypHeader_notCnfShape() throws DCQLException {
        SDJWTCredentialAdapter adapter = new SDJWTCredentialAdapter();

        ParsedCredential parsed = adapter.parse(REAL_DC_SD_JWT_DID_CREDENTIAL);

        // typ header says dc+sd-jwt-did even though cnf uses jwk (not kid) — the fix must trust
        // typ, not infer from cnf shape (an earlier version of this method got this backwards).
        assertEquals("dc+sd-jwt-did", parsed.getFormat());
    }

    @Test
    void plainDcSdJwtCredential_stillClassifiedAsDcSdJwt() throws DCQLException {
        // Same credential structure but typ="dc+sd-jwt" (no -did suffix) — must not regress.
        String header = "eyJhbGciOiJFUzI1NiIsInR5cCI6ImRjK3NkLWp3dCJ9"; // {"alg":"ES256","typ":"dc+sd-jwt"}
        String plainCredential = header + REAL_DC_SD_JWT_DID_CREDENTIAL.substring(
            REAL_DC_SD_JWT_DID_CREDENTIAL.indexOf('.'));

        ParsedCredential parsed = new SDJWTCredentialAdapter().parse(plainCredential);

        assertEquals("dc+sd-jwt", parsed.getFormat());
    }
}
