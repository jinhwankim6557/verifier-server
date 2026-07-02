package org.omnione.did.verifier.v1.protocol.service.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class StatusListTokenFetcherTest {

    private MockRestServiceServer mockServer;
    private StatusListTokenFetcher fetcher;

    @Mock
    private VerifierProperty verifierProperty;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        VerifierProperty.StatusListProperties props = new VerifierProperty.StatusListProperties();
        props.setFailOnFetchError(true);
        props.setMaxCacheTtlSeconds(86400);
        props.setMinCacheTtlSeconds(60);
        when(verifierProperty.getStatusList()).thenReturn(props);

        fetcher = new StatusListTokenFetcher(restTemplate, verifierProperty);
    }

    @Test
    @DisplayName("정상 응답이면 JWT 문자열 반환")
    void fetch_returns_jwt_on_success() {
        mockServer.expect(requestTo("https://issuer.example.com/statuslists/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("header.payload.sig", MediaType.parseMediaType("application/statuslist+jwt")));

        String jwt = fetcher.fetch("https://issuer.example.com/statuslists/1");

        assertThat(jwt).isEqualTo("header.payload.sig");
        mockServer.verify();
    }

    @Test
    @DisplayName("캐시 HIT 이면 HTTP 요청 1회만 발생")
    void fetch_uses_cache_on_second_call() {
        mockServer.expect(requestTo("https://issuer.example.com/statuslists/1"))
                .andRespond(withSuccess("header.payload.sig", MediaType.parseMediaType("application/statuslist+jwt")));

        fetcher.fetch("https://issuer.example.com/statuslists/1");
        fetcher.fetch("https://issuer.example.com/statuslists/1"); // 캐시 HIT — HTTP 호출 없음

        mockServer.verify(); // expect 1회만 등록했으므로 2회 호출 시 실패
    }

    @Test
    @DisplayName("5xx 응답 시 STATUS_LIST_FETCH_FAILED 예외")
    void fetch_throws_on_server_error() {
        mockServer.expect(requestTo("https://issuer.example.com/statuslists/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fetcher.fetch("https://issuer.example.com/statuslists/1"))
                .isInstanceOf(OpenDidException.class)
                .extracting(e -> ((OpenDidException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATUS_LIST_FETCH_FAILED);
    }
}
