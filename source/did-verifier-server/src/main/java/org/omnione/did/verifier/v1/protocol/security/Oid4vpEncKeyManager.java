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

package org.omnione.did.verifier.v1.protocol.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPException;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;
import org.omnione.did.oid4vc.oid4vp.util.crypto.JweResponseDecryptor;
import org.springframework.stereotype.Component;

import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OID4VP JWE 응답 암호화용 트랜잭션별 임시 EC P-256 키쌍 관리(설계 §5.3).
 * 복호화 자체는 SDK({@link JweResponseDecryptor})가 캡슐화하며, 이 클래스는 키·라우팅만 담당한다.
 * alg/enc는 하드코딩하지 않고 {@link VerifierConfigService}(Admin DB 설정, {@code oid4vp_config})에서 읽는다 —
 * 기존 clientMetadata/crypto 설정과 같은 경로(Task 5 상단 메모 참고).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class Oid4vpEncKeyManager {

    private final ObjectMapper objectMapper;
    private final JweResponseDecryptor jweResponseDecryptor;
    private final VerifierConfigService verifierConfigService;

    public ECKey generateEphemeralKeyPair() {
        try {
            String alg = verifierConfigService.getOID4VPConfig().getEncryption().getAlg();
            return new ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.ENCRYPTION)
                    .algorithm(new Algorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (JOSEException e) {
            log.error("Failed to generate ephemeral EC key pair", e);
            throw new OpenDidException(ErrorCode.OID4VP_ENC_KEY_GENERATION_FAILED);
        }
    }

    /** client_metadata에 주입할 jwks + enc 메타(공개키만). InitiationService.initiateVerification의 clientMetadata 인자로 전달한다. */
    public String buildClientMetadataJson(ECKey ephemeralKeyPair) {
        try {
            String enc = verifierConfigService.getOID4VPConfig().getEncryption().getEnc();

            Map<String, Object> jwks = new LinkedHashMap<>();
            jwks.put("keys", List.of(ephemeralKeyPair.toPublicJWK().toJSONObject()));

            Map<String, Object> clientMetadata = new LinkedHashMap<>();
            clientMetadata.put("jwks", jwks);
            clientMetadata.put("encrypted_response_enc_values_supported", List.of(enc));

            return objectMapper.writeValueAsString(clientMetadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize client_metadata jwks", e);
            throw new OpenDidException(ErrorCode.OID4VP_ENC_KEY_GENERATION_FAILED);
        }
    }

    /** 개인키(d 포함) 전체 JWK JSON. Oid4vpSession.encPrivateKeyJwk에 저장한다. */
    public String toStorableJwk(ECKey ephemeralKeyPair) {
        return ephemeralKeyPair.toJSONString();
    }

    public String extractKid(String jweCompact) {
        try {
            return jweResponseDecryptor.parseHeader(jweCompact).getKeyID();
        } catch (OID4VPException e) {
            log.warn("Failed to extract kid from JWE header: {}", e.getErrorMsg());
            throw new OpenDidException(ErrorCode.OID4VP_JWE_HEADER_PARSE_FAILED);
        }
    }

    public ECPrivateKey loadPrivateKey(String storedJwk) {
        try {
            return ECKey.parse(storedJwk).toECPrivateKey();
        } catch (ParseException | JOSEException e) {
            log.error("Failed to load stored enc private key JWK", e);
            throw new OpenDidException(ErrorCode.OID4VP_ENC_KEY_LOAD_FAILED);
        }
    }
}
