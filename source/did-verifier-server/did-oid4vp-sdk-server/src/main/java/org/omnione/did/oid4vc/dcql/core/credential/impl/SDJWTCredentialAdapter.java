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

package org.omnione.did.oid4vc.dcql.core.credential.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.oid4vc.dcql.core.ClaimMatchingHelper;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapter;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;
import org.omnione.did.sdjwt.datamodel.Disclosure;
import org.omnione.did.sdjwt.datamodel.SDJWT;
import org.omnione.did.sdjwt.util.SimpleJWTDecoder;

/**
 * CredentialAdapter implementation for SD-JWT (Selective Disclosure JWT) credentials.
 * Supports formats: vc+sd-jwt, dc+sd-jwt, dc+sd-jwt-did
 */
@Slf4j
public class SDJWTCredentialAdapter implements CredentialAdapter {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("vc+sd-jwt", "dc+sd-jwt", "dc+sd-jwt-did");

    private static final Set<String> RESERVED_CLAIMS = Set.of(
        "iss", "sub", "aud", "exp", "nbf", "iat", "jti",
        "_sd_alg", "_sd", "cnf", "vct"
    );

    @Override
    public Set<String> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public boolean supports(String format) {
        return format != null && SUPPORTED_FORMATS.contains(format);
    }

    @Override
    public ParsedCredential parse(String rawCredential) throws DCQLException {
        if (rawCredential == null || rawCredential.trim().isEmpty()) {
            throw new DCQLException("Raw credential cannot be null or empty");
        }

        try {
            SDJWT sdjwt = SDJWT.parse(rawCredential);
            SimpleJWTDecoder.SimpleJWT jwt = SimpleJWTDecoder.parse(sdjwt.getCredentialJwt());
            JsonNode header = jwt.getHeader();
            Map<String, Object> payload = jwt.getPayloadAsMap();

            // Extract base claims (excluding reserved)
            Map<String, Object> baseClaims = new HashMap<>();
            payload.entrySet().stream()
                .filter(entry -> !RESERVED_CLAIMS.contains(entry.getKey()))
                .forEach(entry -> baseClaims.put(entry.getKey(), entry.getValue()));

            // Extract all claims including disclosures
            Map<String, Object> allClaims = extractAllClaimsInternal(sdjwt, payload);

            // Extract metadata
            Map<String, Object> metadata = extractMetadata(payload);

            // Determine format from vct or default
            String format = determineFormat(header, payload);

            return ParsedCredential.builder()
                .format(format)
                .rawCredential(rawCredential)
                .baseClaims(baseClaims)
                .allClaims(allClaims)
                .metadata(metadata)
                .nativeCredential(sdjwt)
                .build();

        } catch (DCQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DCQLException("Failed to parse SD-JWT credential: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean matchesMetadata(ParsedCredential credential, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }

        try {
            // Check vct_values
            if (metadata.containsKey("vct_values")) {
                @SuppressWarnings("unchecked")
                List<String> requiredVcts = (List<String>) metadata.get("vct_values");
                if (!checkVctValues(credential, requiredVcts)) {
                    log.debug("VCT value matching failed");
                    return false;
                }
            }

            log.debug("All metadata conditions satisfied");
            return true;

        } catch (ClassCastException e) {
            log.error("Invalid metadata format: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> extractAllClaims(ParsedCredential credential) {
        return credential.getAllClaims();
    }

    @Override
    public Set<String> getReservedClaimNames() {
        return RESERVED_CLAIMS;
    }

    @Override
    public Set<String> extractMatchingClaims(ParsedCredential credential,
        List<DCQLQuery.ClaimQuery> claimQueries) {
        return ClaimMatchingHelper.extractMatchingClaimsByPath(credential.getAllClaims(), claimQueries);
    }

    @Override
    public boolean matchesTrustedAuthorities(ParsedCredential credential,
        List<DCQLQuery.TrustedAuthority> trustedAuthorities) {

        if (trustedAuthorities == null || trustedAuthorities.isEmpty()) {
            return true;
        }

        Map<String, Object> metadata = credential.getMetadata();

        for (DCQLQuery.TrustedAuthority authority : trustedAuthorities) {
            if (authority == null || authority.getType() == null || authority.getValues() == null) {
                continue;
            }

            switch (authority.getType()) {
                case "x509_san_dns":
                case "x509_san_uri":
                    // SD-JWT: check iss claim against trusted authority values
                    Object iss = metadata.get("iss");
                    if (iss instanceof String && authority.getValues().contains(iss)) {
                        return true;
                    }
                    break;
                case "aki":
                    // SD-JWT: AKI from x5c certificate chain (if available in metadata)
                    Object aki = metadata.get("aki");
                    if (aki instanceof String && authority.getValues().contains(aki)) {
                        return true;
                    }
                    break;
                default:
                    log.debug("Trusted authority type '{}' not supported for SD-JWT", authority.getType());
                    break;
            }
        }

        return false;
    }

    // ========== Private Helper Methods ==========

    private Map<String, Object> extractAllClaimsInternal(SDJWT sdjwt, Map<String, Object> payload) {
        Map<String, Object> allClaims = new HashMap<>();

        // Add non-reserved claims from payload
        payload.entrySet().stream()
            .filter(entry -> !RESERVED_CLAIMS.contains(entry.getKey()))
            .forEach(entry -> allClaims.put(entry.getKey(), entry.getValue()));

        // Add disclosed claims
        if (sdjwt.getDisclosures() != null && !sdjwt.getDisclosures().isEmpty()) {
            for (Disclosure disclosure : sdjwt.getDisclosures()) {
                if (disclosure.getClaimName() != null) {
                    allClaims.put(disclosure.getClaimName(), disclosure.getClaimValue());
                    log.debug("Added disclosed claim: {} = {}", disclosure.getClaimName(), disclosure.getClaimValue());
                }
            }

            // Integrate nested disclosures
            integrateNestedDisclosures(allClaims, sdjwt);
        }

        return allClaims;
    }

    private void integrateNestedDisclosures(Map<String, Object> allClaims, SDJWT sdjwt) {
        Map<String, Disclosure> digestToDisclosure = new HashMap<>();
        for (Disclosure disclosure : sdjwt.getDisclosures()) {
            String digest = disclosure.digest();
            digestToDisclosure.put(digest, disclosure);
        }

        for (Map.Entry<String, Object> entry : new HashMap<>(allClaims).entrySet()) {
            String claimName = entry.getKey();
            Object claimValue = entry.getValue();

            if (claimValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) claimValue;
                integrateDisclosuresIntoObject(claimName, mapValue, digestToDisclosure);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void integrateDisclosuresIntoObject(String parentName, Map<String, Object> parentObject,
        Map<String, Disclosure> digestToDisclosure) {

        Object sdValue = parentObject.get("_sd");
        if (sdValue instanceof List) {
            List<?> sdArray = (List<?>) sdValue;

            for (Object sdItem : sdArray) {
                if (sdItem instanceof String) {
                    String digest = (String) sdItem;
                    Disclosure disclosure = digestToDisclosure.get(digest);

                    if (disclosure != null && disclosure.getClaimName() != null) {
                        String claimName = disclosure.getClaimName();
                        Object claimValue = disclosure.getClaimValue();

                        parentObject.put(claimName, claimValue);
                        log.debug("Integrated nested disclosure: {}.{} = {}", parentName, claimName, claimValue);

                        if (claimValue instanceof Map) {
                            integrateDisclosuresIntoObject(parentName + "." + claimName,
                                (Map<String, Object>) claimValue, digestToDisclosure);
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> extractMetadata(Map<String, Object> payload) {
        Map<String, Object> metadata = new HashMap<>();

        if (payload.containsKey("vct")) {
            metadata.put("vct", payload.get("vct"));
        }
        if (payload.containsKey("_sd_alg")) {
            metadata.put("_sd_alg", payload.get("_sd_alg"));
        }
        if (payload.containsKey("iss")) {
            metadata.put("iss", payload.get("iss"));
        }
        if (payload.containsKey("cnf")) {
            metadata.put("cnf", payload.get("cnf"));
        }

        return metadata;
    }

    private String determineFormat(JsonNode header, Map<String, Object> payload) {
        // The credential's own JWT header 'typ' is authoritative when present — it's what the
        // Issuer actually stamped (e.g. "dc+sd-jwt-did"), and is more reliable than inferring
        // from payload shape (an earlier version of this method guessed DID-binding from
        // cnf.kid vs cnf.jwk, but real OpenDID dc+sd-jwt-did credentials still use cnf.jwk —
        // the distinguishing signal is typ, not cnf).
        if (header != null && header.has("typ")) {
            String typ = header.get("typ").asText();
            if ("dc+sd-jwt-did".equals(typ) || "dc+sd-jwt".equals(typ) || "vc+sd-jwt".equals(typ)) {
                return typ;
            }
        }
        // No recognized typ — fall back to the vct-presence heuristic.
        if (payload.containsKey("vct")) {
            return "dc+sd-jwt";
        }
        return "vc+sd-jwt";
    }

    private boolean checkVctValues(ParsedCredential credential, List<String> requiredVcts) {
        if (requiredVcts == null || requiredVcts.isEmpty()) {
            return true;
        }

        Object vctValue = credential.getMetadataValue("vct");
        if (vctValue == null) {
            log.debug("SD-JWT has no vct claim");
            return false;
        }

        String actualVct = vctValue.toString();
        boolean matches = requiredVcts.contains(actualVct);

        log.debug("VCT matching: required={}, actual={}, result={}", requiredVcts, actualVct, matches);
        return matches;
    }
}