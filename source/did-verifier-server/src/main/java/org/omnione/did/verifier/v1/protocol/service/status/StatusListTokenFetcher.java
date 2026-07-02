package org.omnione.did.verifier.v1.protocol.service.status;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StatusListTokenFetcher {

    private final RestTemplate restTemplate;
    private final VerifierProperty.StatusListProperties props;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public StatusListTokenFetcher(RestTemplate restTemplate, VerifierProperty verifierProperty) {
        this.restTemplate = restTemplate;
        this.props = verifierProperty.getStatusList();
    }

    public String fetch(String uri) {
        CacheEntry cached = cache.get(uri);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            log.debug("Status list cache HIT for uri: {}", uri);
            return cached.jwt;
        }

        log.debug("Status list cache MISS, fetching: {}", uri);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, String.class);
            String jwt = response.getBody();
            long ttl = parseTtl(jwt);
            cache.put(uri, new CacheEntry(jwt, Instant.now().plusSeconds(ttl)));
            return jwt;
        } catch (OpenDidException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch status list token from {}: {}", uri, e.getMessage());
            throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
        }
    }

    // JWT payload에서 ttl claim 추출. 없으면 minCacheTtlSeconds 사용.
    private long parseTtl(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return props.getMinCacheTtlSeconds();
            String padded = parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4);
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(padded);
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadBytes);
            long ttl = node.path("ttl").asLong(0);
            if (ttl <= 0) ttl = props.getMinCacheTtlSeconds();
            return Math.min(Math.max(ttl, props.getMinCacheTtlSeconds()), props.getMaxCacheTtlSeconds());
        } catch (Exception e) {
            return props.getMinCacheTtlSeconds();
        }
    }

    private record CacheEntry(String jwt, Instant expiresAt) {}
}
