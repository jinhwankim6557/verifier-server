package org.omnione.did.verifier.v1.protocol.service.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.property.VerifierProperty;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialStatusCheckerTest {

    @Mock StatusClaimParser parser;
    @Mock StatusListTokenFetcher fetcher;
    @Mock StatusListTokenVerifier tokenVerifier;
    @Mock StatusListBitDecoder decoder;
    @Mock VerifierProperty verifierProperty;

    private CredentialStatusChecker checker;
    private VerifierProperty.StatusListProperties props;

    private static final String URI = "https://issuer.example.com/statuslists/1";
    private static final String FAKE_SD_JWT = buildFakeSdJwt();
    private static final String FAKE_TOKEN_JWT = "header.payload.sig";

    @BeforeEach
    void setUp() {
        props = new VerifierProperty.StatusListProperties();
        props.setFailOnFetchError(true);
        checker = new CredentialStatusChecker(parser, fetcher, tokenVerifier, decoder, verifierProperty);
    }

    @Test
    @DisplayName("status claim 없으면 통과")
    void no_status_claim_passes() throws Exception {
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.empty());

        assertThatCode(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status = VALID(0) 이면 통과")
    void valid_status_passes() throws Exception {
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenReturn(FAKE_TOKEN_JWT);
        when(tokenVerifier.verify(FAKE_TOKEN_JWT, URI))
                .thenReturn(new StatusListTokenPayload(1, "lst", 3600, 9999999999L));
        when(decoder.extract("lst", 1, 0)).thenReturn(0);

        assertThatCode(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status = INVALID(1) 이면 STATUS_LIST_CREDENTIAL_INVALID 예외")
    void invalid_status_throws() throws Exception {
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenReturn(FAKE_TOKEN_JWT);
        when(tokenVerifier.verify(FAKE_TOKEN_JWT, URI))
                .thenReturn(new StatusListTokenPayload(1, "lst", 3600, 9999999999L));
        when(decoder.extract("lst", 1, 0)).thenReturn(1);

        assertThatThrownBy(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .isInstanceOf(OpenDidException.class)
                .extracting(e -> ((OpenDidException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATUS_LIST_CREDENTIAL_INVALID);
    }

    @Test
    @DisplayName("status = SUSPENDED(2) 이면 STATUS_LIST_CREDENTIAL_SUSPENDED 예외")
    void suspended_status_throws() throws Exception {
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenReturn(FAKE_TOKEN_JWT);
        when(tokenVerifier.verify(FAKE_TOKEN_JWT, URI))
                .thenReturn(new StatusListTokenPayload(2, "lst", 3600, 9999999999L));
        when(decoder.extract("lst", 2, 0)).thenReturn(2);

        assertThatThrownBy(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .isInstanceOf(OpenDidException.class)
                .extracting(e -> ((OpenDidException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATUS_LIST_CREDENTIAL_SUSPENDED);
    }

    @Test
    @DisplayName("fetch 실패 + failOnFetchError=true 이면 STATUS_LIST_FETCH_FAILED 예외")
    void fetch_failure_with_fail_closed_throws() {
        when(verifierProperty.getStatusList()).thenReturn(props);
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenThrow(new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED));

        assertThatThrownBy(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .isInstanceOf(OpenDidException.class)
                .extracting(e -> ((OpenDidException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATUS_LIST_FETCH_FAILED);
    }

    @Test
    @DisplayName("fetch 실패 + failOnFetchError=false 이면 통과 (FAIL-OPEN)")
    void fetch_failure_with_fail_open_passes() {
        props.setFailOnFetchError(false);
        when(verifierProperty.getStatusList()).thenReturn(props);
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenThrow(new OpenDidException(ErrorCode.STATUS_LIST_FETCH_FAILED));

        assertThatCode(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("알 수 없는 status 값(예약값) + failOnFetchError=true 이면 STATUS_LIST_TOKEN_INVALID 예외 (FAIL-CLOSED)")
    void unknown_status_with_fail_closed_throws() throws Exception {
        when(verifierProperty.getStatusList()).thenReturn(props);
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenReturn(FAKE_TOKEN_JWT);
        when(tokenVerifier.verify(FAKE_TOKEN_JWT, URI))
                .thenReturn(new StatusListTokenPayload(4, "lst", 3600, 9999999999L));
        when(decoder.extract("lst", 4, 0)).thenReturn(9);

        assertThatThrownBy(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .isInstanceOf(OpenDidException.class)
                .extracting(e -> ((OpenDidException) e).getErrorCode())
                .isEqualTo(ErrorCode.STATUS_LIST_TOKEN_INVALID);
    }

    @Test
    @DisplayName("알 수 없는 status 값(예약값) + failOnFetchError=false 이면 통과 (FAIL-OPEN)")
    void unknown_status_with_fail_open_passes() throws Exception {
        props.setFailOnFetchError(false);
        when(verifierProperty.getStatusList()).thenReturn(props);
        when(parser.parse(FAKE_SD_JWT)).thenReturn(Optional.of(new StatusListRef(0, URI)));
        when(fetcher.fetch(URI)).thenReturn(FAKE_TOKEN_JWT);
        when(tokenVerifier.verify(FAKE_TOKEN_JWT, URI))
                .thenReturn(new StatusListTokenPayload(4, "lst", 3600, 9999999999L));
        when(decoder.extract("lst", 4, 0)).thenReturn(9);

        assertThatCode(() -> checker.checkAll(Map.of("cred1", List.of(FAKE_SD_JWT))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SD-JWT가 아닌 포맷(Map)은 건너뜀")
    void non_sdjwt_credential_is_skipped() throws Exception {
        Map<String, Object> jsonVp = Map.of("type", "VerifiablePresentation");

        assertThatCode(() -> checker.checkAll(Map.of("cred1", List.of(jsonVp))))
                .doesNotThrowAnyException();

        verifyNoInteractions(parser, fetcher, tokenVerifier, decoder);
    }

    private static String buildFakeSdJwt() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"ES256\",\"kid\":\"did:omn:issuer#assert\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"did:omn:issuer\"}".getBytes());
        return header + "." + payload + ".sig~disc~";
    }
}
