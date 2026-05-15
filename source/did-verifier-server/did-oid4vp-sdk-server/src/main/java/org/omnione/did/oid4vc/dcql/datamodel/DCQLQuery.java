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

package org.omnione.did.oid4vc.dcql.datamodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DCQLQuery {

  @JsonProperty("credentials")
  private List<CredentialQuery> credentials;

  @JsonProperty("credential_sets")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<CredentialSet> credentialSets;

  @JsonProperty("transaction_data")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<Map<String, Object>> transactionData;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CredentialQuery {

    @JsonProperty("id")
    private String id;

    @JsonProperty("format")
    private String format;

    @JsonProperty("meta")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> meta;

    @JsonProperty("claims")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ClaimQuery> claims;

    /**
     * Per OID4VP spec: claim_sets is an array of arrays of claim query IDs.
     * Each inner array references claim IDs defined in the 'claims' array.
     * Example: [["a", "b"], ["a", "b", "c"]]
     */
    @JsonProperty("claim_sets")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<List<String>> claimSets;

    @JsonProperty("trusted_authorities")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TrustedAuthority> trustedAuthorities;

    @JsonProperty("purpose")
    private String purpose;

    /**
     * If true, the Wallet MAY return multiple credentials for this query.
     * Defaults to false (exactly one credential expected).
     */
    @JsonProperty("multiple")
    private Boolean multiple;

    @JsonProperty("require_cryptographic_holder_binding")
    private Boolean requireCryptographicHolderBinding;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ClaimQuery {

    @JsonProperty("id")
    private String id;

    /**
     * For JSON-based credentials (SD-JWT, W3C VC): path to the claim.
     * Not used for mdoc format.
     */
    @JsonProperty("path")
    private List<Object> path;

    /**
     * For mdoc credentials: the namespace of the claim.
     * e.g., "org.iso.18013.5.1"
     */
    @JsonProperty("namespace")
    private String namespace;

    /**
     * For mdoc credentials: the name of the claim within the namespace.
     * e.g., "family_name"
     */
    @JsonProperty("claim_name")
    private String claimName;

    @JsonProperty("purpose")
    private String purpose;

    @JsonProperty("values")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Object> values;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("max")
    private Object max;

    @JsonProperty("min")
    private Object min;
  }

  /**
   * Trusted authority for credential issuer validation.
   * Per OID4VP spec section 6.1.1.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class TrustedAuthority {

    /**
     * Type of authority validation.
     * Allowed values: "aki", "etsi_tl", "openid_federation", "x509_san_dns", "x509_san_uri"
     */
    @JsonProperty("type")
    private String type;

    @JsonProperty("values")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> values;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CredentialSet {

    @JsonProperty("id")
    private String id;

    @JsonProperty("options")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<List<String>> options;

    /**
     * Per OID4VP spec: whether at least one option must be satisfied.
     * Defaults to true if not specified.
     */
    @JsonProperty("required")
    private Boolean required;

    @JsonProperty("purpose")
    private String purpose;
  }
}