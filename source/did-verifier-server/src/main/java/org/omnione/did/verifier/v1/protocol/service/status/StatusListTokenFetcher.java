package org.omnione.did.verifier.v1.protocol.service.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StatusListTokenFetcher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final VerifierProperty.StatusListProperties props;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public StatusListTokenFetcher(RestTemplate restTemplate, VerifierProperty verifierProperty) {
        this.restTemplate = restTemplate;
        this.props = verifierProperty.getStatusList();
    }

    public String fetch(String uri) {
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
        }
        validateNotPrivateNetwork(parsed);

        CacheEntry cached = cache.get(uri);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            log.debug("Status list cache HIT for uri: {}", uri);
            return cached.jwt;
        }

        log.debug("Status list cache MISS, fetching: {}", uri);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, String.class);
            // WebConfig의 RestTemplate은 리다이렉트를 자동으로 따라가지 않도록 설정돼 있다.
            // 3xx가 그대로 응답으로 오면(SSRF 우회 시도일 수 있음) 실패로 처리한다 — 재요청하지 않는다.
            if (response.getStatusCode().is3xxRedirection()) {
                log.warn("Status list host returned a redirect ({}) for uri: {} — not following", response.getStatusCode(), uri);
                throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
            }
            String jwt = response.getBody();
            if (jwt == null) {
                log.error("Status list token response body is null for uri: {}", uri);
                throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
            }
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

    /**
     * SSRF 방지: scheme=https 검사만으로는 내부망 호스트를 막지 못한다(commit 710e214의 미완성 지점).
     * 호스트를 실제로 resolve해서 나온 IP가 loopback/사설망(RFC1918)/link-local(169.254.x.x 클라우드
     * 메타데이터 대역 포함)/멀티캐스트/wildcard 면 거부한다. DNS rebinding까지 완전히 막지는 못하지만
     * (요청 시점 재검증), scheme 검사 하나만 있던 것보다는 훨씬 좁혀진다.
     */
    private void validateNotPrivateNetwork(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                        || address.isMulticastAddress()) {
                    log.warn("Rejected status list uri resolving to a non-public address: {} -> {}", host, address);
                    throw new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED);
                }
            }
        } catch (UnknownHostException e) {
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
                    OBJECT_MAPPER.readTree(payloadBytes);
            long ttl = node.path("ttl").asLong(0);
            if (ttl <= 0) ttl = props.getMinCacheTtlSeconds();
            return Math.min(Math.max(ttl, props.getMinCacheTtlSeconds()), props.getMaxCacheTtlSeconds());
        } catch (Exception e) {
            return props.getMinCacheTtlSeconds();
        }
    }

    private record CacheEntry(String jwt, Instant expiresAt) {}
}
