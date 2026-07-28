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

package org.omnione.did.oid4vc.dcql.core;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.core.credential.impl.SDJWTCredentialAdapter;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies dc+sd-jwt selective disclosure end-to-end through
 * {@link SDJWTCredentialAdapter} and {@link DCQLCredentialMatcher}, using a real
 * signed SD-JWT with exactly one disclosed claim (same fixture as
 * Oid4vpClaimExtractionServiceTest in did-verifier-server).
 */
class DCQLSelectiveDisclosureTest {

    // typ=dc+sd-jwt-did, cnf.jwk, single disclosure: date_of_issuance=""
    private static final String REAL_DC_SD_JWT_DID_CREDENTIAL =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpvbW46aXNzdWVyP3ZlcnNpb25JZD0xI2Fzc2VydCIsInR5cCI6ImRjK3NkLWp3dC1kaWQifQ"
                    + ".eyJpc3MiOiJkaWQ6b21uOmlzc3VlciIsImlhdCI6MTc4MzkwNjg5NCwiZXhwIjoxODE1NDQyODk0LCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsImNuZiI6eyJqd2siOnsia3R5IjoiRUMiLCJ1c2UiOiJzaWciLCJjcnYiOiJQLTI1NiIsIngiOiJCSnA4Si1QSzVnN3F3a3lwWS1kWEVQSGd2TGJvWndlNWtSX2EwQ0tkazl3IiwieSI6IlBnRWZIV0hXdzFOd0wtT2lyREk3a1lUVUVyWHJNX3RLZHBON2ZQU2ZYVG8ifX0sIl9zZCI6WyJrbXlTbVZjOFJ6UzJGc2FFRnlKQUxrc1ZaTFkyR0FjNENSdmh4NVlSYWJnIl19"
                    + ".8QcbKfKViLDAEV1Jce07cg62Eml9UauMkO7hCYD2lS1uH49FLgFOKy1V64wFnTGXmz9PoQxnLxszBmjaugY74Q"
                    + "~WyJyUzBqVEVYWExSbDlVUmJXekNvQTRnIiwiZGF0ZV9vZl9pc3N1YW5jZSIsIiJd"
                    + "~eyJhbGciOiJFUzI1NiIsInR5cCI6ImtiK2p3dCJ9.eyJhdWQiOiJkZWNlbnRyYWxpemVkX2lkZW50aWZpZXI6ZGlkOm9tbjp2ZXJpZmllciIsIm5vbmNlIjoiYzJiNzhlZDMtZGUyZC00MDc3LTk5ZDMtZGY0OTU4NGYyODM3IiwiaWF0IjoxNzg0MDgwNDIxLCJzZF9oYXNoIjoiV0JqY19GazBsanlfcTBjZzVtLVIyWmVaQzA2TFdYbHJ2bEMzQlRUZFdUSSJ9.Aea3fPRjBoFQyVRPIe1JjQaOxkYZgbkyrzA9WpnVzW0pytlh9SeYxtFl2QUwNMVj4tZ6FwqOKhRHm7KNKO3E_w";

    private final SDJWTCredentialAdapter adapter = new SDJWTCredentialAdapter();

    private ParsedCredential parse() throws DCQLException {
        return adapter.parse(REAL_DC_SD_JWT_DID_CREDENTIAL);
    }

    @Test
    void verifyClaimConstraintsSucceedsForDisclosedClaim() throws DCQLException {
        ParsedCredential credential = parse();
        List<DCQLQuery.ClaimQuery> claims = List.of(
                DCQLQuery.ClaimQuery.builder().path(List.of("date_of_issuance")).build());

        String result = DCQLCredentialMatcher.verifyClaimConstraints(credential, claims);

        assertThat(result).isNull();
    }

    @Test
    void verifyClaimConstraintsFailsForUndisclosedClaim() throws DCQLException {
        ParsedCredential credential = parse();
        List<DCQLQuery.ClaimQuery> claims = List.of(
                DCQLQuery.ClaimQuery.builder().path(List.of("family_name")).build());

        String result = DCQLCredentialMatcher.verifyClaimConstraints(credential, claims);

        assertThat(result).isNotNull();
        assertThat(result).contains("family_name");
    }

    @Test
    void nullClaimsOnQueryIncludesAllDisclosedClaims() throws DCQLException {
        ParsedCredential credential = parse();
        DCQLQuery dcqlQuery = DCQLQuery.builder()
                .credentials(List.of(DCQLQuery.CredentialQuery.builder()
                        .format("dc+sd-jwt")
                        .claims(null)
                        .build()))
                .build();

        Set<String> matched = DCQLCredentialMatcher.extractMatchingClaimNames(dcqlQuery, credential);

        assertThat(matched).contains("date_of_issuance");
    }

    @Test
    void emptyClaimsOnQueryExcludesAllClaims() throws DCQLException {
        ParsedCredential credential = parse();
        DCQLQuery dcqlQuery = DCQLQuery.builder()
                .credentials(List.of(DCQLQuery.CredentialQuery.builder()
                        .format("dc+sd-jwt")
                        .claims(Collections.emptyList())
                        .build()))
                .build();

        Set<String> matched = DCQLCredentialMatcher.extractMatchingClaimNames(dcqlQuery, credential);

        assertThat(matched).isEmpty();
    }
}
