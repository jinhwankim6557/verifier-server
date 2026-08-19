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

import com.upokecenter.cbor.CBORObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.mdoc.core.oid4vp.MDocParser;
import org.omnione.did.mdoc.exception.MdocException;
import org.omnione.did.oid4vc.dcql.core.ClaimMatchingHelper;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapter;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;

/**
 * CredentialAdapter implementation for mDoc (ISO 18013-5) credentials.
 *
 * <p>Supports formats: mso_mdoc (x5chain-based issuer identification) and mso_mdoc-did
 * (DID-native, issuer identified by the kid in the IssuerAuth unprotected header).
 * As with SD-JWT, the credential's own content decides which one it is.
 */
@Slf4j
public class MDocCredentialAdapter implements CredentialAdapter {

    private static final String FORMAT_MDOC = "mso_mdoc";
    private static final String FORMAT_MDOC_DID = "mso_mdoc-did";

    private static final Set<String> SUPPORTED_FORMATS = Set.of(FORMAT_MDOC, FORMAT_MDOC_DID);

    private static final Set<String> RESERVED_CLAIMS = Set.of(
        "docType", "version", "status", "validityInfo", "deviceKeyInfo"
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
            CBORObject deviceResponse = MDocParser.decodeDeviceResponse(rawCredential);
            CBORObject document = MDocParser.extractFirstDocument(rawCredential);

            String docType = document.get("docType") != null
                ? document.get("docType").AsString()
                : "unknown";

            Map<String, Object> allClaims = extractClaimsFromNameSpaces(document);

            // If issuerSigned.nameSpaces is empty, try to extract claim names from MSO valueDigests
            if (allClaims.isEmpty() || allClaims.values().stream()
                    .allMatch(v -> v instanceof Map && ((Map<?, ?>) v).isEmpty())) {
                log.warn("No claim values found in issuerSigned.nameSpaces for docType: {}. "
                    + "Wallet may not have disclosed IssuerSignedItems.", docType);
                Map<String, Object> claimNamesFromMso = extractClaimNamesFromMSO(document);
                if (!claimNamesFromMso.isEmpty()) {
                    allClaims = claimNamesFromMso;
                }
            }

            Map<String, Object> baseClaims = new HashMap<>(allClaims);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docType", docType);

            CBORObject version = deviceResponse.get("version");
            if (version != null) {
                metadata.put("version", version.AsString());
            }

            CBORObject status = deviceResponse.get("status");
            if (status != null) {
                metadata.put("status", status.AsInt32Value());
            }

            return ParsedCredential.builder()
                .format(resolveFormat(rawCredential))
                .rawCredential(rawCredential)
                .baseClaims(baseClaims)
                .allClaims(allClaims)
                .metadata(metadata)
                .nativeCredential(deviceResponse)
                .build();

        } catch (DCQLException e) {
            throw e;
        } catch (MdocException e) {
            throw new DCQLException("Failed to parse mDoc credential: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DCQLException("Failed to parse mDoc credential: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the concrete format from the credential itself: an IssuerAuth carrying a kid
     * (COSE label 4) is DID-native, otherwise the issuer is identified by the x5chain.
     */
    private String resolveFormat(String rawCredential) {
        try {
            String kid = MDocParser.extractIssuerKid(rawCredential);
            return kid != null && !kid.isBlank() ? FORMAT_MDOC_DID : FORMAT_MDOC;
        } catch (Exception e) {
            log.debug("Failed to read issuer kid, treating credential as {}: {}", FORMAT_MDOC, e.getMessage());
            return FORMAT_MDOC;
        }
    }

    @Override
    public boolean matchesMetadata(ParsedCredential credential, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }

        // Check doctype_value (OID4VP spec standard parameter - single value match)
        if (metadata.containsKey("doctype_value")) {
            String requiredDocType = metadata.get("doctype_value").toString();
            Object actualDocType = credential.getMetadataValue("docType");
            if (actualDocType == null || !requiredDocType.equals(actualDocType.toString())) {
                log.debug("doctype_value mismatch: required={}, actual={}", requiredDocType, actualDocType);
                return false;
            }
        }

        log.debug("All mDoc metadata conditions satisfied");
        return true;
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

        if (claimQueries == null || claimQueries.isEmpty()) {
            return credential.getAllClaims().keySet();
        }

        Set<String> matchingClaims = new HashSet<>();
        Map<String, Object> allClaims = credential.getAllClaims();

        for (DCQLQuery.ClaimQuery claimQuery : claimQueries) {
            String namespace = claimQuery.getNamespace();
            String claimName = claimQuery.getClaimName();

            if (namespace != null && claimName != null) {
                // mdoc-native: namespace + claim_name
                Object nsData = allClaims.get(namespace);
                if (nsData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nsMap = (Map<String, Object>) nsData;
                    Object value = nsMap.get(claimName);
                    if (value != null && ClaimMatchingHelper.meetsConditions(claimQuery, value)) {
                        matchingClaims.add(namespace + "." + claimName);
                        log.debug("mdoc claim matched: {}.{}", namespace, claimName);
                    }
                }
            } else if (claimQuery.getPath() != null && !claimQuery.getPath().isEmpty()) {
                // Fallback: path-based for backward compatibility
                matchingClaims.addAll(
                    ClaimMatchingHelper.extractMatchingClaimsByPath(allClaims, List.of(claimQuery)));
            }
        }

        return matchingClaims;
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
                    // mDoc: issuer identity from MSO certificate
                    Object issuer = metadata.get("x509_san_dns");
                    if (issuer == null) {
                        issuer = metadata.get("x509_san_uri");
                    }
                    if (issuer instanceof String && authority.getValues().contains(issuer)) {
                        return true;
                    }
                    break;
                case "aki":
                    // mDoc: Authority Key Identifier from MSO certificate chain
                    Object aki = metadata.get("aki");
                    if (aki instanceof String && authority.getValues().contains(aki)) {
                        return true;
                    }
                    break;
                default:
                    log.debug("Trusted authority type '{}' not supported for mDoc", authority.getType());
                    break;
            }
        }

        return false;
    }

    /**
     * Extracts claims from mDoc nameSpaces structure.
     * Iterates through issuerSigned.nameSpaces and extracts elementIdentifier/elementValue pairs.
     */
    private Map<String, Object> extractClaimsFromNameSpaces(CBORObject document) {
        Map<String, Object> claims = new HashMap<>();

        CBORObject issuerSigned = document.get("issuerSigned");
        if (issuerSigned == null) {
            return claims;
        }

        CBORObject nameSpaces = issuerSigned.get("nameSpaces");
        if (nameSpaces == null) {
            return claims;
        }

        for (CBORObject nsKey : nameSpaces.getKeys()) {
            String nameSpace = nsKey.AsString();
            CBORObject nsItems = nameSpaces.get(nsKey);

            Map<String, Object> nsClaims = new HashMap<>();
            if (nsItems != null && nsItems.size() > 0) {
                for (int i = 0; i < nsItems.size(); i++) {
                    CBORObject item = nsItems.get(i);
                    CBORObject decoded = item;
                    if (item.isTagged()) {
                        decoded = CBORObject.DecodeFromBytes(item.GetByteString());
                    }

                    CBORObject elementId = decoded.get("elementIdentifier");
                    CBORObject elementValue = decoded.get("elementValue");

                    if (elementId != null) {
                        nsClaims.put(elementId.AsString(), cborToJava(elementValue));
                    }
                }
            }

            claims.put(nameSpace, nsClaims);
        }

        return claims;
    }

    /**
     * Extracts claim names from MSO valueDigests when issuerSigned.nameSpaces is empty.
     * The MSO payload is inside issuerAuth COSE_Sign1 structure.
     * Returns a map with namespace -> { claimName: "(not disclosed)" }
     */
    private Map<String, Object> extractClaimNamesFromMSO(CBORObject document) {
        Map<String, Object> claims = new HashMap<>();

        try {
            CBORObject issuerSigned = document.get("issuerSigned");
            if (issuerSigned == null) {
                return claims;
            }

            CBORObject issuerAuth = issuerSigned.get("issuerAuth");
            if (issuerAuth == null || issuerAuth.size() < 3) {
                return claims;
            }

            // issuerAuth is COSE_Sign1: [protected, unprotected, payload, signature]
            CBORObject payload = issuerAuth.get(2);
            if (payload == null) {
                return claims;
            }

            // Payload is tagged CBOR (tag 24) containing MSO bytes
            CBORObject mso;
            if (payload.isTagged()) {
                mso = CBORObject.DecodeFromBytes(payload.GetByteString());
            } else if (payload.getType() == com.upokecenter.cbor.CBORType.ByteString) {
                mso = CBORObject.DecodeFromBytes(payload.GetByteString());
            } else {
                mso = payload;
            }

            CBORObject valueDigests = mso.get("valueDigests");
            if (valueDigests == null) {
                return claims;
            }

            // valueDigests: { namespace -> { digestID(int) -> digest(bytes) } }
            for (CBORObject nsKey : valueDigests.getKeys()) {
                String nameSpace = nsKey.AsString();
                CBORObject digests = valueDigests.get(nsKey);
                int claimCount = digests != null ? digests.size() : 0;

                Map<String, Object> nsClaims = new HashMap<>();
                nsClaims.put("_info", claimCount + " claim(s) present but values not disclosed by wallet");
                claims.put(nameSpace, nsClaims);

                log.debug("MSO valueDigests: namespace='{}', {} digest entries", nameSpace, claimCount);
            }
        } catch (Exception e) {
            log.warn("Failed to extract claim names from MSO: {}", e.getMessage());
        }

        return claims;
    }

    /**
     * Converts a CBORObject to its Java equivalent.
     */
    private Object cborToJava(CBORObject cbor) {
        if (cbor == null || cbor.isNull()) {
            return null;
        }

        // Handle tagged values (e.g., tag 0 = date-time string, tag 1003 = date)
        if (cbor.isTagged()) {
            CBORObject untagged = cbor.Untag();
            if (untagged.getType() == com.upokecenter.cbor.CBORType.TextString) {
                return untagged.AsString();
            }
            return cborToJava(untagged);
        }

        switch (cbor.getType()) {
            case TextString:
                return cbor.AsString();
            case Integer:
                return cbor.AsInt32Value();
            case Boolean:
                return cbor.AsBoolean();
            case FloatingPoint:
                return cbor.AsDouble();
            case ByteString:
                return java.util.Base64.getEncoder().encodeToString(cbor.GetByteString());
            case Array:
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < cbor.size(); i++) {
                    list.add(cborToJava(cbor.get(i)));
                }
                return list;
            case Map:
                Map<String, Object> map = new HashMap<>();
                for (CBORObject key : cbor.getKeys()) {
                    map.put(key.AsString(), cborToJava(cbor.get(key)));
                }
                return map;
            default:
                return cbor.toString();
        }
    }
}
