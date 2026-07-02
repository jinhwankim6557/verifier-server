package org.omnione.did.verifier.v1.protocol.service.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;

@Component
public class StatusClaimParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<StatusListRef> parse(String sdJwt) {
        try {
            String issuerJwt = sdJwt.split("~", 2)[0];
            String[] parts = issuerJwt.split("\\.");
            if (parts.length < 2) return Optional.empty();

            byte[] payloadBytes = Base64.getUrlDecoder().decode(pad(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadBytes);

            JsonNode statusList = payload.path("status").path("status_list");
            if (statusList.isMissingNode()) return Optional.empty();

            JsonNode idxNode = statusList.get("idx");
            JsonNode uriNode = statusList.get("uri");
            if (idxNode == null || uriNode == null) return Optional.empty();

            return Optional.of(new StatusListRef(idxNode.asInt(), uriNode.asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String pad(String base64url) {
        int padding = (4 - base64url.length() % 4) % 4;
        return base64url + "=".repeat(padding);
    }
}
