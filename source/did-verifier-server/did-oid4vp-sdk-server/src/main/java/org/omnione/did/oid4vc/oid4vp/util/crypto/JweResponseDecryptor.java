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

package org.omnione.did.oid4vc.oid4vp.util.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPErrorCode;
import org.omnione.did.oid4vc.oid4vp.exception.OID4VPException;
import org.springframework.stereotype.Component;

import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;

/**
 * OID4VP 응답(direct_post.jwt) JWE 복호화. ECDH-ES + A256GCM 고정(설계 §2, §9).
 */
@Slf4j
@Component
public class JweResponseDecryptor {

    /** 복호화 결과. mdoc 후속 연동(§12)을 위해 protected header를 함께 보존한다. */
    public static class DecryptedResponse {
        private final String plaintext;
        private final JWEHeader protectedHeader;

        public DecryptedResponse(String plaintext, JWEHeader protectedHeader) {
            this.plaintext = plaintext;
            this.protectedHeader = protectedHeader;
        }

        public String getPlaintext() {
            return plaintext;
        }

        public JWEHeader getProtectedHeader() {
            return protectedHeader;
        }
    }

    /** 복호화 없이 protected header만 읽는다(평문이므로 kid 추출에 안전). */
    public JWEHeader parseHeader(String jweCompact) throws OID4VPException {
        if (jweCompact == null) {
            log.warn("Failed to parse JWE header: jweCompact is null");
            throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_HEADER_PARSE_FAILED, "jweCompact must not be null");
        }
        try {
            return JWEObject.parse(jweCompact).getHeader();
        } catch (ParseException e) {
            log.warn("Failed to parse JWE header: {}", e.getMessage());
            throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_HEADER_PARSE_FAILED, e.getMessage(), e);
        }
    }

    public DecryptedResponse decrypt(String jweCompact, ECPrivateKey recipientPrivateKey) throws OID4VPException {
        if (jweCompact == null) {
            log.warn("Failed to decrypt JWE response: jweCompact is null");
            throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_DECRYPT_FAILED, "jweCompact must not be null");
        }
        if (recipientPrivateKey == null) {
            log.warn("Failed to decrypt JWE response: recipientPrivateKey is null");
            throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_DECRYPT_FAILED, "recipientPrivateKey must not be null");
        }
        try {
            JWEObject jweObject = JWEObject.parse(jweCompact);
            JWEHeader header = jweObject.getHeader();
            if (!JWEAlgorithm.ECDH_ES.equals(header.getAlgorithm()) || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())) {
                String message = String.format(
                        "Unsupported JWE alg/enc combination: alg=%s, enc=%s (expected alg=%s, enc=%s)",
                        header.getAlgorithm(), header.getEncryptionMethod(), JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM);
                log.warn("Failed to decrypt JWE response: {}", message);
                throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_DECRYPT_FAILED, message);
            }
            jweObject.decrypt(new ECDHDecrypter(recipientPrivateKey));
            return new DecryptedResponse(jweObject.getPayload().toString(), jweObject.getHeader());
        } catch (ParseException | JOSEException e) {
            log.warn("Failed to decrypt JWE response: {}", e.getMessage());
            throw new OID4VPException(OID4VPErrorCode.ERR_CODE_CRYPTO_JWE_DECRYPT_FAILED, e.getMessage(), e);
        }
    }
}
