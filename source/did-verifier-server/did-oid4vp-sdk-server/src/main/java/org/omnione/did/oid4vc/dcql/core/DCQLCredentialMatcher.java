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
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapter;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapterRegistry;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;

/**
 * Format-agnostic credential matcher for DCQL queries.
 * Delegates format-specific operations to CredentialAdapter implementations.
 */
@Slf4j
public class DCQLCredentialMatcher {

  private static final CredentialAdapterRegistry registry = CredentialAdapterRegistry.getInstance();

  /**
   * Checks if the required format is supported.
   */
  public static boolean matchesFormat(String requiredFormat) {
    if (requiredFormat == null) {
      return true;
    }

    boolean isSupported = registry.isFormatSupported(requiredFormat);
    if (!isSupported) {
      log.info("Unsupported format: {}", requiredFormat);
    }
    return isSupported;
  }

  /**
   * Checks if the credential matches the metadata requirements.
   * Delegates to the format-specific adapter.
   */
  public static boolean matchesMetadata(ParsedCredential credential, Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return true;
    }

    Optional<CredentialAdapter> adapterOpt = registry.findAdapter(credential.getFormat());
    if (adapterOpt.isEmpty()) {
      log.warn("No adapter found for format: {}", credential.getFormat());
      return false;
    }

    return adapterOpt.get().matchesMetadata(credential, metadata);
  }

  /**
   * Checks if the credential's issuer matches any of the trusted authorities.
   * Delegates to the format-specific adapter.
   */
  public static boolean matchesTrustedAuthorities(ParsedCredential credential,
      List<DCQLQuery.TrustedAuthority> trustedAuthorities) {

    if (trustedAuthorities == null || trustedAuthorities.isEmpty()) {
      return true;
    }

    Optional<CredentialAdapter> adapterOpt = registry.findAdapter(credential.getFormat());
    if (adapterOpt.isEmpty()) {
      log.warn("No adapter found for format: {}", credential.getFormat());
      return false;
    }

    return adapterOpt.get().matchesTrustedAuthorities(credential, trustedAuthorities);
  }

  /**
   * Verifies claim-level DCQL constraints against a parsed credential.
   *
   * <p>Each {@code ClaimQuery} is evaluated independently via the format-specific
   * adapter's {@code extractMatchingClaims}. A claim is considered satisfied when
   * the adapter returns a non-empty match set for it.
   *
   * <p>{@code claim_sets} is not evaluated here; callers that need claim_sets
   * semantics must handle it separately.
   *
   * @return {@code null} if all claim queries are satisfied, otherwise a short
   *     human-readable descriptor of the first unsatisfied claim query
   */
  public static String verifyClaimConstraints(ParsedCredential credential,
      List<DCQLQuery.ClaimQuery> claimQueries) throws DCQLException {

    if (claimQueries == null || claimQueries.isEmpty()) {
      return null;
    }

    Optional<CredentialAdapter> adapterOpt = registry.findAdapter(credential.getFormat());
    if (adapterOpt.isEmpty()) {
      throw new DCQLException("No adapter registered for format: " + credential.getFormat());
    }
    CredentialAdapter adapter = adapterOpt.get();

    for (DCQLQuery.ClaimQuery claimQuery : claimQueries) {
      Set<String> matched = adapter.extractMatchingClaims(
          credential, Collections.singletonList(claimQuery));

      if (matched == null || matched.isEmpty()) {
        String descriptor = describeClaimQuery(claimQuery);
        log.debug("DCQL claim verification failed for {}", descriptor);
        return descriptor;
      }
    }
    return null;
  }

  private static String describeClaimQuery(DCQLQuery.ClaimQuery claimQuery) {
    if (claimQuery.getNamespace() != null && claimQuery.getClaimName() != null) {
      return claimQuery.getNamespace() + "." + claimQuery.getClaimName();
    }
    if (claimQuery.getPath() != null) {
      return "path=" + claimQuery.getPath();
    }
    if (claimQuery.getId() != null) {
      return "id=" + claimQuery.getId();
    }
    return "<unknown claim>";
  }

  /**
   * Extracts matching claim names based on DCQL query.
   * Delegates format-specific claim matching to the appropriate adapter.
   */
  public static Set<String> extractMatchingClaimNames(DCQLQuery dcqlQuery, ParsedCredential credential) {
    if (dcqlQuery == null || dcqlQuery.getCredentials() == null || credential == null) {
      return Collections.emptySet();
    }

    Set<String> matchingClaims = new HashSet<>();

    dcqlQuery.getCredentials().forEach(credentialQuery -> {
      if (credentialQuery.getClaims() == null) {
        matchingClaims.addAll(credential.getAllClaims().keySet());
        log.info("credential.claims is null, including all claims: {}", credential.getAllClaims().keySet());
        return;
      }

      if (credentialQuery.getClaims().isEmpty()) {
        log.info("credential.claims is empty, excluding claims");
        return;
      }

      // Resolve effective claims considering claim_sets
      List<DCQLQuery.ClaimQuery> effectiveClaims =
          resolveEffectiveClaims(credentialQuery.getClaims(), credentialQuery.getClaimSets());

      // Delegate to adapter for format-specific matching
      Optional<CredentialAdapter> adapterOpt = registry.findAdapter(credentialQuery.getFormat());
      if (adapterOpt.isPresent()) {
        matchingClaims.addAll(
            adapterOpt.get().extractMatchingClaims(credential, effectiveClaims));
      } else {
        // Fallback: use path-based matching
        log.warn("No adapter for format '{}', using path-based fallback", credentialQuery.getFormat());
        matchingClaims.addAll(
            ClaimMatchingHelper.extractMatchingClaimsByPath(credential.getAllClaims(), effectiveClaims));
      }
    });

    return matchingClaims;
  }

  /**
   * Resolves effective claims considering claim_sets.
   * If claim_sets is defined, returns claims referenced by any claim_set.
   * Otherwise returns all claims.
   */
  private static List<DCQLQuery.ClaimQuery> resolveEffectiveClaims(
      List<DCQLQuery.ClaimQuery> claims, List<List<String>> claimSets) {

    if (claimSets == null || claimSets.isEmpty()) {
      return claims;
    }

    // Collect all claim IDs referenced by any claim_set
    Set<String> allReferencedIds = new HashSet<>();
    for (List<String> claimSet : claimSets) {
      allReferencedIds.addAll(claimSet);
    }

    List<DCQLQuery.ClaimQuery> resolved = new ArrayList<>();
    for (DCQLQuery.ClaimQuery claim : claims) {
      if (claim.getId() != null && allReferencedIds.contains(claim.getId())) {
        resolved.add(claim);
      }
    }

    return resolved.isEmpty() ? claims : resolved;
  }

  /**
   * Parses a raw credential string using the appropriate adapter.
   */
  public static ParsedCredential parseCredential(String rawCredential, String format) throws DCQLException {
    Optional<CredentialAdapter> adapterOpt;

    if (format != null) {
      adapterOpt = registry.findAdapter(format);
    } else {
      adapterOpt = registry.detectAdapter(rawCredential);
    }

    if (adapterOpt.isEmpty()) {
      throw new DCQLException("No adapter found for credential format: " + format);
    }

    return adapterOpt.get().parse(rawCredential);
  }

  /**
   * Validates that a set of presented credential IDs satisfies at least one option
   * in each required credential_set.
   *
   * @param credentialSets the credential_sets from the DCQL query
   * @param presentedCredentialIds the set of credential IDs actually presented
   * @return list of error messages; empty if valid
   */
  public static List<String> validateCredentialSetsSatisfied(
      List<DCQLQuery.CredentialSet> credentialSets,
      Set<String> presentedCredentialIds) {

    if (credentialSets == null || credentialSets.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> errors = new ArrayList<>();

    for (DCQLQuery.CredentialSet credentialSet : credentialSets) {
      // Default required = true per spec
      boolean required = credentialSet.getRequired() == null || credentialSet.getRequired();

      if (!required) {
        log.debug("Skipping non-required credential_set: {}", credentialSet.getId());
        continue;
      }

      boolean anySatisfied = false;
      for (List<String> option : credentialSet.getOptions()) {
        if (presentedCredentialIds.containsAll(option)) {
          anySatisfied = true;
          break;
        }
      }

      if (!anySatisfied) {
        String setId = credentialSet.getId() != null ? credentialSet.getId() : "(unnamed)";
        errors.add("credential_set '" + setId
            + "' not satisfied: none of its options are fully covered by presented credentials "
            + presentedCredentialIds);
      }
    }

    return errors;
  }
}
