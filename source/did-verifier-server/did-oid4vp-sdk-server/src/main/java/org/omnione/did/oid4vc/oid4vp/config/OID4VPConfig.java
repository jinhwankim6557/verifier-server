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

package org.omnione.did.oid4vc.oid4vp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
public class OID4VPConfig {

    @JsonProperty("baseUrl")
    private String baseUrl;

    @JsonProperty("clientName")
    private String clientName;

    @JsonProperty("invocationScheme")
    private String invocationScheme = "openid4vp://";

    @JsonProperty("clientId")
    private ClientId clientId = new ClientId();

    @JsonProperty("session")
    private Session session = new Session();

    @JsonProperty("endpoints")
    private Endpoints endpoints = new Endpoints();

    @JsonProperty("clientMetadata")
    private ClientMetadata clientMetadata = new ClientMetadata();

    @JsonProperty("crypto")
    private Crypto crypto = new Crypto();

    @JsonProperty("encryption")
    private Encryption encryption = new Encryption();

    @JsonProperty("verification")
    private Verification verification = new Verification();

    @Data
    public static class Session {
        @JsonProperty("sessionTtl")
        private long sessionTtl = 300000;
    }

    @Data
    public static class Endpoints {
        @JsonProperty("response")
        private String response = "/oid4vp/response";

        @JsonProperty("request")
        private String request = "/oid4vp/request";

        @JsonProperty("fragmentCallback")
        private String fragmentCallback = "/oid4vp/fragment/callback";
    }

    @Data
    public static class ClientMetadata {
        @JsonProperty("vpFormatsSupported")
        private Map<String, Object> vpFormatsSupported = Collections.emptyMap();
    }

    @Data
    public static class Crypto {
        @JsonProperty("vpTokenEncryptionKey")
        private String vpTokenEncryptionKey;
    }

    /**
     * NOTE: {@code alg}/{@code enc} here are effectively fixed and display-only from this
     * SDK's perspective. {@link org.omnione.did.oid4vc.oid4vp.util.crypto.JweResponseDecryptor#decrypt}
     * unconditionally enforces {@code ECDH-ES} + {@code A256GCM} regardless of these config
     * values — this is a deliberate security choice (fixed algorithm, no negotiation).
     * The separate consuming application reads these fields to generate ephemeral keys and to
     * advertise {@code encrypted_response_enc_values_supported} to wallets, but changing this
     * config does NOT change what this SDK will actually accept when decrypting a
     * {@code direct_post.jwt} response. If an operator edits {@code enc}/{@code alg} via the
     * Admin UI to anything other than {@code ECDH-ES}/{@code A256GCM}, the consuming app would
     * advertise a value this SDK will reject, causing a silent mismatch — keep these in sync
     * manually.
     */
    @Data
    public static class Encryption {
        @JsonProperty("alg")
        private String alg = "ECDH-ES";

        @JsonProperty("enc")
        private String enc = "A256GCM";
    }

    public String getResponseUrl() {
        return baseUrl + endpoints.getResponse();
    }

    public String getRequestUrl() {
        return baseUrl + endpoints.getRequest();
    }

    public String getFragmentCallbackUrl() {
        return baseUrl + endpoints.getFragmentCallback();
    }

    public String getInvocationScheme() {
        return invocationScheme;
    }

    public String buildClientId() {
      return clientId.getScheme() + ":" + clientId.getValue();
    }

    @Data
    public static class Verification {
        @JsonProperty("skipX5cChainValidation")
        private boolean skipX5cChainValidation = false;

        @JsonProperty("enforceClaimConstraints")
        private boolean enforceClaimConstraints = false;
    }

    @Data
    public static class ClientId {
        @JsonProperty("scheme")
        private String scheme;

        @JsonProperty("value")
        private String value;
    }
}