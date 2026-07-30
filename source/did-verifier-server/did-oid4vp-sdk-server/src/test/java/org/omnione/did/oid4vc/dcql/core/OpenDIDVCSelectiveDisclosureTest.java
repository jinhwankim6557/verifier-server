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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.core.credential.impl.OpenDIDVCCredentialAdapter;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;

/**
 * Verifies opendid_vc selective disclosure end-to-end through
 * {@link OpenDIDVCCredentialAdapter} and {@link DCQLCredentialMatcher}, using the DCQL
 * request shape discussed for the "student_id" credential:
 *
 * <pre>
 * {
 *   "credentials": [{
 *     "id": "student_id",
 *     "format": "opendid_vc",
 *     "meta": { "credential_schema_id_values": ["https://campusid.omnione.org/issuer/api/v1/vc/vcschema?name=student_id"] },
 *     "claims": [
 *       { "path": ["org.omnione.campusid.first_name"] },
 *       { "path": ["org.omnione.campusid.last_name"] }
 *     ]
 *   }]
 * }
 * </pre>
 *
 * <p>opendid_vc claim "path" is a single-element array holding the claim's full code
 * (namespace + schemaId concatenated, e.g. "org.omnione.campusid.first_name"), unlike
 * mdoc's nested [namespace, name] path.
 */
class OpenDIDVCSelectiveDisclosureTest {

    private static final String SCHEMA_ID =
            "https://campusid.omnione.org/issuer/api/v1/vc/vcschema?name=student_id";

    // Minimal opendid_vc VP: two claims (first_name, last_name), full-VC proofValue.
    private static final String STUDENT_ID_VP = """
        {
          "@context": ["https://www.w3.org/ns/credentials/v2"],
          "id": "vp-student-id-1",
          "type": ["VerifiablePresentation"],
          "holder": "did:omn:holder",
          "verifierNonce": "nonce-1",
          "verifiableCredential": [
            {
              "@context": ["https://www.w3.org/ns/credentials/v2"],
              "id": "vc-student-id-1",
              "type": ["VerifiableCredential"],
              "issuer": { "id": "did:omn:issuer", "name": "campusid issuer" },
              "credentialSchema": {
                "id": "%s",
                "type": "OsdSchemaCredential"
              },
              "credentialSubject": {
                "id": "did:omn:holder",
                "claims": [
                  { "code": "org.omnione.campusid.first_name", "caption": "First Name", "type": "text", "format": "plain", "value": "Gildong" },
                  { "code": "org.omnione.campusid.last_name", "caption": "Last Name", "type": "text", "format": "plain", "value": "Hong" }
                ]
              },
              "proof": {
                "type": "Secp256r1Signature2018",
                "created": "2026-01-01T00:00:00Z",
                "verificationMethod": "did:omn:issuer?versionId=1#assert",
                "proofPurpose": "assertionMethod",
                "proofValue": "dummy-signature-not-checked-by-dcql-matcher"
              }
            }
          ],
          "proof": {
            "type": "Secp256r1Signature2018",
            "created": "2026-01-01T00:00:00Z",
            "verificationMethod": "did:omn:holder?versionId=1#auth",
            "proofPurpose": "authentication",
            "challenge": "nonce-1",
            "proofValue": "dummy-signature-not-checked-by-dcql-matcher"
          }
        }
        """.formatted(SCHEMA_ID);

    private final OpenDIDVCCredentialAdapter adapter = new OpenDIDVCCredentialAdapter();

    private ParsedCredential parse() throws DCQLException {
        return adapter.parse(STUDENT_ID_VP);
    }

    private DCQLQuery.CredentialQuery studentIdCredentialQuery() {
        return DCQLQuery.CredentialQuery.builder()
                .id("student_id")
                .format("opendid_vc")
                .meta(Map.of("credential_schema_id_values", List.of(SCHEMA_ID)))
                .claims(List.of(
                        DCQLQuery.ClaimQuery.builder()
                                .path(List.of("org.omnione.campusid.first_name")).build(),
                        DCQLQuery.ClaimQuery.builder()
                                .path(List.of("org.omnione.campusid.last_name")).build()))
                .build();
    }

    @Test
    void matchesMetadataAgainstCredentialSchemaIdValues() throws DCQLException {
        ParsedCredential credential = parse();

        boolean matches = adapter.matchesMetadata(credential, studentIdCredentialQuery().getMeta());

        assertTrue(matches);
    }

    @Test
    void rejectsMetadataWhenSchemaIdDiffers() throws DCQLException {
        ParsedCredential credential = parse();
        Map<String, Object> otherSchemaMeta = Map.of("credential_schema_id_values",
                List.of("https://other-issuer.example.org/vcschema?name=other"));

        boolean matches = adapter.matchesMetadata(credential, otherSchemaMeta);

        assertTrue(!matches);
    }

    @Test
    void extractsBothDisclosedClaimsByFullCodePath() throws DCQLException {
        ParsedCredential credential = parse();
        DCQLQuery.CredentialQuery query = studentIdCredentialQuery();

        Set<String> matched = adapter.extractMatchingClaims(credential, query.getClaims());

        assertEquals(Set.of("org.omnione.campusid.first_name", "org.omnione.campusid.last_name"), matched);
    }

    @Test
    void verifyClaimConstraintsSucceedsForBothClaims() throws DCQLException {
        ParsedCredential credential = parse();
        DCQLQuery.CredentialQuery query = studentIdCredentialQuery();

        String result = DCQLCredentialMatcher.verifyClaimConstraints(credential, query.getClaims());

        assertEquals(null, result);
    }

    @Test
    void verifyClaimConstraintsFailsForUnknownClaimCode() throws DCQLException {
        ParsedCredential credential = parse();
        List<DCQLQuery.ClaimQuery> claims = List.of(
                DCQLQuery.ClaimQuery.builder()
                        .path(List.of("org.omnione.campusid.student_number")).build());

        String result = DCQLCredentialMatcher.verifyClaimConstraints(credential, claims);

        assertTrue(result != null);
    }
}
