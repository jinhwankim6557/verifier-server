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

    // OpenDIDVCCredentialAdapter.extractAllClaimsFromMap() 참고: opendid_vc 포맷은 claim이
    // {code, caption, type, format, hideValue, value} 통짜 객체로 들어있다(다른 포맷의 평범한 값과 다름).
    private static final String OPENDID_VC_FORMAT = "opendid_vc";

    // mdoc은 claim이 namespace 한 겹 아래에 모여 있다: {namespace: {elementIdentifier: value}}.
    // 그대로 두면 화면에 namespace 한 줄 + 객체 통짜가 찍히므로 element 단위로 펴서 보여준다.
    private static final String MDOC_FORMAT = "mso_mdoc";
    private static final String MDOC_DID_FORMAT = "mso_mdoc-did";

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
                                addClaimViews(result, parsed.getFormat(), key, value));
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

    /** mdoc은 namespace 맵을 풀어 element마다 한 줄씩, 그 외 포맷은 그대로 한 줄을 만든다. */
    private void addClaimViews(List<ClaimView> result, String format, String key, Object value) {
        if (isMdocFormat(format) && value instanceof Map<?, ?> nameSpace) {
            nameSpace.forEach((element, elementValue) ->
                    result.add(new ClaimView(String.valueOf(element), stringifyClaimValue(elementValue))));
            return;
        }
        result.add(toClaimView(format, key, value));
    }

    private boolean isMdocFormat(String format) {
        return MDOC_FORMAT.equals(format) || MDOC_DID_FORMAT.equals(format);
    }

    // opendid_vc는 value가 {caption, value, ...} 디스크립터 객체이므로 caption/value를 풀어서 꺼낸다.
    // 그 외 포맷(dc+sd-jwt 등)은 지금까지처럼 value를 그대로 claim 값으로 사용한다.
    private ClaimView toClaimView(String format, String key, Object value) {
        if (OPENDID_VC_FORMAT.equals(format) && value instanceof Map<?, ?> claimMap) {
            Object caption = claimMap.get("caption");
            String captionStr = (caption instanceof String cs && !cs.isBlank()) ? cs : key;
            return new ClaimView(captionStr, stringifyClaimValue(claimMap.get("value")));
        }
        return new ClaimView(key, stringifyClaimValue(value));
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
