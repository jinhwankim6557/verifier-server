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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.omnione.did.opendidvc.datamodel.VerifiablePresentation;

/**
 * CredentialAdapter implementation for OpenDID Verifiable Credentials.
 * Supports format: opendid_vc (W3C Verifiable Credentials JSON-LD format)
 */
@Slf4j
public class OpenDIDVCCredentialAdapter implements CredentialAdapter {

    private static final String FORMAT_TYPE = "opendid_vc";
    private static final Set<String> SUPPORTED_FORMATS = Set.of(FORMAT_TYPE);

    private static final Set<String> RESERVED_CLAIMS = Set.of(
        "@context", "id", "type", "issuer", "issuanceDate", "expirationDate",
        "validFrom", "validUntil", "credentialSubject", "credentialSchema",
        "credentialStatus", "proof", "holder", "verifiableCredential"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // Parse as Map for flexible access to all fields
            Map<String, Object> vpMap = objectMapper.readValue(rawCredential,
                new TypeReference<Map<String, Object>>() {});

            // Also parse as VerifiablePresentation for nativeCredential
            VerifiablePresentation vp = objectMapper.readValue(rawCredential,
                VerifiablePresentation.class);

            // Get verifiableCredential array
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> vcList = (List<Map<String, Object>>) vpMap.get("verifiableCredential");
            if (vcList == null || vcList.isEmpty()) {
                throw new DCQLException("VP does not contain any Verifiable Credentials");
            }

            Map<String, Object> firstVCMap = vcList.get(0);
            Map<String, Object> allClaims = extractAllClaimsFromMap(firstVCMap);
            Map<String, Object> baseClaims = extractBaseClaims(allClaims);
            Map<String, Object> metadata = extractMetadataFromMap(vpMap, firstVCMap);

            log.debug("Parsed OpenDID VC - metadata: {}", metadata);

            return ParsedCredential.builder()
                .format(FORMAT_TYPE)
                .rawCredential(rawCredential)
                .baseClaims(baseClaims)
                .allClaims(allClaims)
                .metadata(metadata)
                .nativeCredential(vp)
                .build();

        } catch (DCQLException e) {
            throw e;
        } catch (Exception e) {
            throw new DCQLException("Failed to parse OpenDID VC credential: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean matchesMetadata(ParsedCredential credential, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }

        try {
            // Check credential_schema_id_values
            if (metadata.containsKey("credential_schema_id_values")) {
                @SuppressWarnings("unchecked")
                List<String> requiredSchemaIds = (List<String>) metadata.get("credential_schema_id_values");
                if (!checkCredentialSchemaIdValues(credential, requiredSchemaIds)) {
                    log.debug("Credential schema ID matching failed");
                    return false;
                }
            }

            log.debug("All metadata conditions satisfied for OpenDID VC");
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

        Map<String, Object> allClaims = credential.getAllClaims();

        if (claimQueries == null || claimQueries.isEmpty()) {
            return new HashSet<>(allClaims.keySet());
        }

        Set<String> matched = new HashSet<>();
        for (DCQLQuery.ClaimQuery claimQuery : claimQueries) {
            List<Object> path = claimQuery.getPath();
            if (path == null || path.isEmpty() || !(path.get(0) instanceof String)) {
                log.debug("OpenDID VC claim query skipped: path must start with a String code");
                continue;
            }
            if (path.size() > 1) {
                log.debug("OpenDID VC claim query: path elements beyond [0] are ignored (code-only lookup)");
            }

            String code = (String) path.get(0);
            Object claimEntry = allClaims.get(code);
            if (!(claimEntry instanceof Map)) {
                log.debug("OpenDID VC claim not found for code: {}", code);
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> claimMap = (Map<String, Object>) claimEntry;
            Object claimValue = claimMap.get("value");

            if (ClaimMatchingHelper.meetsConditions(claimQuery, claimValue)) {
                matched.add(code);
                log.debug("OpenDID VC claim matched: {}", code);
            }
        }
        return matched;
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
                    // OpenDID VC: check issuer field
                    Object issuer = metadata.get("issuer");
                    if (issuer instanceof String && authority.getValues().contains(issuer)) {
                        return true;
                    }
                    break;
                default:
                    log.debug("Trusted authority type '{}' not supported for OpenDID VC", authority.getType());
                    break;
            }
        }

        return false;
    }

    // ========== Private Helper Methods ==========

    /**
     * Extracts claims from an OpenDID VC credentialSubject.
     *
     * <p>OpenDID VC stores claims as a list of Claim objects under
     * {@code credentialSubject.claims}, each with fields
     * {@code code, caption, type, format, hideValue, value}. This method
     * flattens that list into a map keyed by {@code code}, with the whole
     * Claim object as the value so downstream can access any field.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAllClaimsFromMap(Map<String, Object> vcMap) {
        Map<String, Object> allClaims = new HashMap<>();

        Object credentialSubject = vcMap.get("credentialSubject");
        if (credentialSubject instanceof Map) {
            collectClaimsFromSubject((Map<String, Object>) credentialSubject, allClaims);
        } else if (credentialSubject instanceof List) {
            for (Object subject : (List<?>) credentialSubject) {
                if (subject instanceof Map) {
                    collectClaimsFromSubject((Map<String, Object>) subject, allClaims);
                }
            }
        }

        log.debug("Extracted {} OpenDID VC claims (keyed by code)", allClaims.size());
        return allClaims;
    }

    @SuppressWarnings("unchecked")
    private void collectClaimsFromSubject(Map<String, Object> subjectMap, Map<String, Object> out) {
        Object claimsObj = subjectMap.get("claims");
        if (!(claimsObj instanceof List)) {
            return;
        }
        for (Object item : (List<?>) claimsObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> claimMap = (Map<String, Object>) item;
            Object code = claimMap.get("code");
            if (code instanceof String) {
                out.put((String) code, claimMap);
            }
        }
    }

    private Map<String, Object> extractBaseClaims(Map<String, Object> allClaims) {
        // For OpenDID VC, base claims are same as all claims (no selective disclosure)
        return new HashMap<>(allClaims);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadataFromMap(Map<String, Object> vpMap, Map<String, Object> vcMap) {
        Map<String, Object> metadata = new HashMap<>();

        // Extract credential schema ID from VC map
        Object credentialSchema = vcMap.get("credentialSchema");
        if (credentialSchema instanceof Map) {
            Map<String, Object> schemaMap = (Map<String, Object>) credentialSchema;
            if (schemaMap.containsKey("id")) {
                metadata.put("credential_schema_id", schemaMap.get("id"));
                log.debug("Extracted credential_schema_id: {}", schemaMap.get("id"));
            }
            if (schemaMap.containsKey("type")) {
                metadata.put("credential_schema_type", schemaMap.get("type"));
            }
        }

        // Extract issuer info
        Object issuer = vcMap.get("issuer");
        if (issuer instanceof String) {
            metadata.put("issuer", issuer);
        } else if (issuer instanceof Map) {
            Map<String, Object> issuerMap = (Map<String, Object>) issuer;
            if (issuerMap.containsKey("id")) {
                metadata.put("issuer", issuerMap.get("id"));
            }
        }

        // Extract holder info from VP map
        Object holder = vpMap.get("holder");
        if (holder != null) {
            metadata.put("holder", holder);
        }

        // Extract VC types
        Object vcTypes = vcMap.get("type");
        if (vcTypes != null) {
            metadata.put("vc_types", vcTypes);
        }

        return metadata;
    }

    private boolean checkCredentialSchemaIdValues(ParsedCredential credential, List<String> requiredSchemaIds) {
        if (requiredSchemaIds == null || requiredSchemaIds.isEmpty()) {
            return true;
        }

        Object schemaIdValue = credential.getMetadataValue("credential_schema_id");
        if (schemaIdValue == null) {
            log.debug("OpenDID VC has no credential_schema_id");
            return false;
        }

        String actualSchemaId = schemaIdValue.toString();
        boolean matches = requiredSchemaIds.contains(actualSchemaId);

        log.debug("Credential schema ID matching: required={}, actual={}, result={}",
            requiredSchemaIds, actualSchemaId, matches);
        return matches;
    }
}
