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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapterRegistry;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;

public class DCQLQueryValidator {

  private static final Set<String> TRUSTED_AUTHORITY_TYPES = Set.of(
      "aki", "etsi_tl", "openid_federation", "x509_san_dns", "x509_san_uri"
  );

  private static final Set<String> MDOC_FORMATS = Set.of("mso_mdoc", "mso_mdoc-did");

  public static ValidationResult validate(DCQLQuery dcqlQuery) {
    ValidationResult result = new ValidationResult();

    if (dcqlQuery == null) {
      result.addError("DCQL query is null");
      return result;
    }

    validateBasicStructure(dcqlQuery, result);

    if (dcqlQuery.getCredentials() != null) {
      validateCredentials(dcqlQuery.getCredentials(), result);
    }

    if (dcqlQuery.getCredentialSets() != null) {
      validateCredentialSets(dcqlQuery.getCredentialSets(), dcqlQuery.getCredentials(), result);
    }

    validateConsistency(dcqlQuery, result);

    return result;
  }

  private static void validateBasicStructure(DCQLQuery dcqlQuery, ValidationResult result) {
    // Per OID4VP spec: credentials is REQUIRED
    boolean hasCredentials =
        dcqlQuery.getCredentials() != null && !dcqlQuery.getCredentials().isEmpty();

    if (!hasCredentials) {
      result.addError("DCQL query must have 'credentials' (REQUIRED per spec)");
    }

    // credential_sets is OPTIONAL and works together with credentials
  }

  private static void validateCredentials(List<DCQLQuery.CredentialQuery> credentials,
      ValidationResult result) {
    if (credentials.isEmpty()) {
      result.addError("'credentials' array cannot be empty");
      return;
    }

    Set<String> credentialIds = new HashSet<>();

    for (int i = 0; i < credentials.size(); i++) {
      DCQLQuery.CredentialQuery credential = credentials.get(i);
      String context = "credentials[" + i + "]";

      validateCredential(credential, context, result);

      if (credential.getId() != null) {
        if (credentialIds.contains(credential.getId())) {
          result.addError("Duplicate credential ID: " + credential.getId());
        } else {
          credentialIds.add(credential.getId());
        }
      }
    }
  }

  private static void validateCredential(DCQLQuery.CredentialQuery credential, String context,
      ValidationResult result) {
    if (credential == null) {
      result.addError(context + " is null");
      return;
    }

    validateRequiredField(credential.getId(), "id", context, result);
    validateRequiredField(credential.getFormat(), "format", context, result);

    if (credential.getId() != null) {
      validateCredentialId(credential.getId(), context, result);
    }

    if (credential.getFormat() != null) {
      validateFormat(credential.getFormat(), context, result);
    }

    if (credential.getClaims() != null) {
      boolean isMdoc = credential.getFormat() != null && MDOC_FORMATS.contains(credential.getFormat());
      validateClaims(credential.getClaims(), context, isMdoc, result);
    }

    if (credential.getClaimSets() != null) {
      validateClaimSets(credential.getClaimSets(), credential.getClaims(), context, result);
    }

    if (credential.getMeta() != null) {
      validateMeta(credential.getMeta(), context, result);
    }

    if (credential.getTrustedAuthorities() != null) {
      validateTrustedAuthorities(credential.getTrustedAuthorities(), context, result);
    }
  }

  private static void validateClaims(List<DCQLQuery.ClaimQuery> claims, String context,
      boolean isMdoc, ValidationResult result) {

    Set<String> claimIds = new HashSet<>();

    for (int i = 0; i < claims.size(); i++) {
      DCQLQuery.ClaimQuery claim = claims.get(i);
      String claimContext = context + ".claims[" + i + "]";

      if (claim == null) {
        result.addError(claimContext + " is null");
        continue;
      }

      // Validate claim id uniqueness if present
      if (claim.getId() != null) {
        if (claimIds.contains(claim.getId())) {
          result.addError(claimContext + " has duplicate claim ID: " + claim.getId());
        } else {
          claimIds.add(claim.getId());
        }
      }

      if (isMdoc) {
        // mdoc format: accepts namespace+claim_name (spec) or path (EUDI interop)
        boolean hasNsClaim = claim.getNamespace() != null && claim.getClaimName() != null;
        boolean hasPath = claim.getPath() != null && !claim.getPath().isEmpty();

        if (!hasNsClaim && !hasPath) {
          result.addError(claimContext + " requires either namespace+claim_name or path for mso_mdoc format");
        }
        if (hasPath) {
          validatePath(claim.getPath(), claimContext + ".path", result);
        }
      } else {
        // JSON-based format: path required
        if (claim.getPath() == null || claim.getPath().isEmpty()) {
          result.addError(claimContext + ".path is required and cannot be empty for non-mdoc format");
        } else {
          validatePath(claim.getPath(), claimContext + ".path", result);
        }
      }

      if (claim.getValues() != null) {
        validateValues(claim.getValues(), claimContext + ".values", result);
      }
    }
  }

  private static void validatePath(List<Object> path, String context, ValidationResult result) {
    if (path == null) {
      result.addError(context + " cannot be null");
      return;
    }

    if (!DCQLPathProcessor.isValidPath(path)) {
      result.addError(context + " contains invalid elements");
    }

    for (int i = 0; i < path.size(); i++) {
      Object element = path.get(i);
      if (element == null) {
        continue;
      } else if (element instanceof String) {
        String strElement = (String) element;
        if (strElement.trim().isEmpty()) {
          result.addError(context + "[" + i + "] cannot be empty string");
        }
      } else if (!(element instanceof Integer)) {
        result.addError(context + "[" + i + "] must be string, integer, or null");
      }
    }
  }

  private static void validateValues(List<Object> values, String context, ValidationResult result) {
    if (values.isEmpty()) {
      result.addWarning(context + " is empty - no value restrictions will be applied");
    }

    Set<Class<?>> valueTypes = new HashSet<>();
    for (Object value : values) {
      if (value != null) {
        valueTypes.add(value.getClass());
      }
    }

    if (valueTypes.size() > 1) {
      result.addWarning(context + " contains mixed value types - may cause matching issues");
    }
  }

  /**
   * Validates claim_sets per OID4VP spec: array of arrays of claim ID strings.
   * Each inner array references claim IDs defined in the 'claims' array.
   */
  private static void validateClaimSets(List<List<String>> claimSets,
      List<DCQLQuery.ClaimQuery> claims, String context, ValidationResult result) {

    if (claimSets.isEmpty()) {
      result.addError(context + ".claim_sets cannot be empty");
      return;
    }

    if (claims == null || claims.isEmpty()) {
      result.addError(context + ".claim_sets requires 'claims' to be defined with IDs");
      return;
    }

    // Collect all available claim IDs
    Set<String> availableClaimIds = new HashSet<>();
    for (DCQLQuery.ClaimQuery claim : claims) {
      if (claim.getId() != null) {
        availableClaimIds.add(claim.getId());
      }
    }

    if (availableClaimIds.isEmpty()) {
      result.addError(context + ".claim_sets requires claims to have 'id' fields");
      return;
    }

    for (int i = 0; i < claimSets.size(); i++) {
      List<String> claimSet = claimSets.get(i);
      String setContext = context + ".claim_sets[" + i + "]";

      if (claimSet == null || claimSet.isEmpty()) {
        result.addError(setContext + " cannot be null or empty");
        continue;
      }

      for (String claimId : claimSet) {
        if (!availableClaimIds.contains(claimId)) {
          result.addError(setContext + " references undefined claim ID: " + claimId);
        }
      }
    }
  }

  private static void validateCredentialSets(List<DCQLQuery.CredentialSet> credentialSets,
      List<DCQLQuery.CredentialQuery> credentials,
      ValidationResult result) {
    if (credentialSets.isEmpty()) {
      result.addError("'credential_sets' array cannot be empty");
      return;
    }

    Set<String> availableCredentialIds = new HashSet<>();
    if (credentials != null) {
      credentials.stream()
          .map(DCQLQuery.CredentialQuery::getId)
          .filter(Objects::nonNull)
          .forEach(availableCredentialIds::add);
    }

    for (int i = 0; i < credentialSets.size(); i++) {
      DCQLQuery.CredentialSet credentialSet = credentialSets.get(i);
      String context = "credential_sets[" + i + "]";

      validateCredentialSet(credentialSet, context, availableCredentialIds, result);
    }
  }

  private static void validateCredentialSet(DCQLQuery.CredentialSet credentialSet,
      String context,
      Set<String> availableCredentialIds,
      ValidationResult result) {
    if (credentialSet == null) {
      result.addError(context + " is null");
      return;
    }

    if (credentialSet.getOptions() == null || credentialSet.getOptions().isEmpty()) {
      result.addError(context + ".options is required and cannot be empty");
      return;
    }

    for (int i = 0; i < credentialSet.getOptions().size(); i++) {
      List<String> option = credentialSet.getOptions().get(i);
      String optionContext = context + ".options[" + i + "]";

      if (option == null || option.isEmpty()) {
        result.addError(optionContext + " cannot be null or empty");
        continue;
      }

      for (String credentialId : option) {
        if (!availableCredentialIds.contains(credentialId)) {
          result.addError(optionContext + " references undefined credential ID: " + credentialId);
        }
      }
    }
  }

  /**
   * Validates trusted_authorities per OID4VP spec section 6.1.1.
   */
  private static void validateTrustedAuthorities(List<DCQLQuery.TrustedAuthority> authorities,
      String context, ValidationResult result) {

    if (authorities.isEmpty()) {
      result.addWarning(context + ".trusted_authorities is empty");
      return;
    }

    for (int i = 0; i < authorities.size(); i++) {
      DCQLQuery.TrustedAuthority authority = authorities.get(i);
      String authContext = context + ".trusted_authorities[" + i + "]";

      if (authority == null) {
        result.addError(authContext + " is null");
        continue;
      }

      if (authority.getType() == null || authority.getType().trim().isEmpty()) {
        result.addError(authContext + ".type is required");
      } else if (!TRUSTED_AUTHORITY_TYPES.contains(authority.getType())) {
        result.addWarning(authContext + ".type '" + authority.getType()
            + "' is not a recognized type. Known types: " + TRUSTED_AUTHORITY_TYPES);
      }

      if (authority.getValues() == null || authority.getValues().isEmpty()) {
        result.addError(authContext + ".values is required and cannot be empty");
      }
    }
  }

  private static void validateConsistency(DCQLQuery dcqlQuery, ValidationResult result) {
    // Additional consistency checks can be added here
  }

  private static void validateRequiredField(String value, String fieldName, String context,
      ValidationResult result) {
    if (value == null || value.trim().isEmpty()) {
      result.addError(context + "." + fieldName + " is required");
    }
  }

  private static void validateCredentialId(String id, String context, ValidationResult result) {
    if (!id.matches("^[a-zA-Z0-9_-]+$")) {
      result.addError(
          context + ".id must contain only alphanumeric characters, underscores, and hyphens");
    }
  }

  private static void validateFormat(String format, String context, ValidationResult result) {
    Set<String> supportedFormats = CredentialAdapterRegistry.getInstance().getAllSupportedFormats();

    Set<String> knownFormats = new HashSet<>(supportedFormats);
    knownFormats.addAll(Set.of("jwt_vc_json", "jwt_vc", "ldp_vc", "mso_mdoc", "mso_mdoc-did"));

    if (!knownFormats.contains(format)) {
      result.addWarning(context + ".format '" + format + "' may not be supported");
    }
  }

  private static void validateMeta(Map<String, Object> meta, String context,
      ValidationResult result) {

    if (meta.isEmpty()) {
      result.addWarning(context + ".meta is empty");
    }

    if (meta.containsKey("vct_values")) {
      Object vctValues = meta.get("vct_values");
      if (!(vctValues instanceof List)) {
        result.addError(context + ".meta.vct_values must be an array");
      }
    }

    if (meta.containsKey("doctype_value")) {
      Object doctypeValue = meta.get("doctype_value");
      if (!(doctypeValue instanceof String)) {
        result.addError(context + ".meta.doctype_value must be a string");
      }
    }
  }

  public static class ValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String error) {
      errors.add(error);
    }

    public void addWarning(String warning) {
      warnings.add(warning);
    }

    public List<String> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
      return Collections.unmodifiableList(warnings);
    }

    public boolean isValid() {
      return errors.isEmpty();
    }

    public boolean hasWarnings() {
      return !warnings.isEmpty();
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    public String getSummary() {
      return String.format("Validation: %d errors, %d warnings", errors.size(), warnings.size());
    }
  }
}
