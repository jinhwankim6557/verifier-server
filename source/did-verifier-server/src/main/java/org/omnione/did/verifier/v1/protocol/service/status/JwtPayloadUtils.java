package org.omnione.did.verifier.v1.protocol.service.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

final class JwtPayloadUtils {

    private JwtPayloadUtils() {
    }

    static String padBase64Url(String base64url) {
        int padding = (4 - base64url.length() % 4) % 4;
        return base64url + "=".repeat(padding);
    }

    static byte[] decodeBase64Url(String base64url) {
        return Base64.getUrlDecoder().decode(padBase64Url(base64url));
    }

    static JsonNode parseJwtPayload(ObjectMapper objectMapper, String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a valid JWT: missing payload segment");
        }
        return objectMapper.readTree(decodeBase64Url(parts[1]));
    }
}
