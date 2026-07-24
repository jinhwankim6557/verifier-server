package org.omnione.did.verifier.v1.protocol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.oid4vc.dcql.core.credential.CredentialAdapterRegistry;
import org.omnione.did.oid4vc.dcql.core.credential.ParsedCredential;
import org.omnione.did.oid4vc.dcql.exception.DCQLException;
import org.omnione.did.oid4vc.oid4vp.service.OID4VPHelperService;
import org.omnione.did.verifier.v1.protocol.api.dto.ClaimView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 저장된 vp_token을 confirm 화면 표시용 claim 목록으로 재파싱한다.
 * 재검증이 아니라 표시 전용이므로 실패해도 상태 조회 자체는 깨지지 않아야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Oid4vpClaimExtractionService {

    private final OID4VPHelperService oid4VPHelperService;
    private final ObjectMapper objectMapper;

    public List<ClaimView> extractClaims(String vpTokenJson) {
        if (vpTokenJson == null || vpTokenJson.isBlank()) {
            return List.of();
        }
        List<ClaimView> claims = new ArrayList<>();
        try {
            Map<String, List<Object>> vpTokenMap = oid4VPHelperService.parseVPToken(vpTokenJson);
            for (List<Object> credentials : vpTokenMap.values()) {
                for (Object credentialObj : credentials) {
                    claims.addAll(extractFromCredential(credentialObj));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract claims for display from vp_token", e);
        }
        return claims;
    }

    List<ClaimView> extractFromCredential(Object credentialObj) {
        String raw = toRawString(credentialObj);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        return CredentialAdapterRegistry.getInstance().detectAdapter(raw)
                .map(adapter -> {
                    try {
                        ParsedCredential parsed = adapter.parse(raw);
                        List<ClaimView> result = new ArrayList<>();
                        parsed.getAllClaims().forEach((key, value) ->
                                result.add(new ClaimView(key, stringifyClaimValue(value))));
                        return result;
                    } catch (DCQLException e) {
                        log.warn("Failed to parse credential for claim display: {}", e.getMessage());
                        return List.<ClaimView>of();
                    }
                })
                .orElseGet(() -> {
                    log.debug("No credential adapter detected for claim display");
                    return List.of();
                });
    }

    private String toRawString(Object credentialObj) {
        if (credentialObj instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(credentialObj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String stringifyClaimValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
