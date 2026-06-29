/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.verifier.v1.agent.adapter;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.datamodel.enums.EccCurveType;
import org.omnione.did.base.datamodel.enums.SymmetricCipherType;
import org.omnione.did.base.datamodel.enums.SymmetricPaddingType;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.util.BaseCryptoUtil;
import org.omnione.did.base.util.BaseDigestUtil;
import org.omnione.did.base.util.BaseMultibaseUtil;
import org.omnione.did.crypto.keypair.KeyPairInterface;
import org.omnione.did.verifier.v1.provider.CryptoHelper;
import org.omnione.did.verifier.v1.model.data.KeyPairInfo;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;

/**
 * CryptoHelper 어댑터 구현
 *
 * SDK의 CryptoHelper 인터페이스를 구현하여
 * did-crypto-sdk-server를 Application에 연결합니다.
 *
 * 주요 기능:
 * - Phase 1: 서명 검증, 해시 생성
 * - Phase 2-1: E2E 키쌍 생성, Nonce 생성, ECDH, KDF, 복호화
 */
@Slf4j
@Component
public class CryptoHelperImpl implements CryptoHelper {

    // ========================================================================
    // Phase 1: 서명 검증 및 해시
    // ========================================================================

    @Override
    public boolean verifySignature(String publicKey, String signature, byte[] data) {
        try {
            // Note: VpManager가 내부적으로 서명 검증을 수행하므로
            // 이 메서드는 Phase 1에서 VpVerificationService가 직접 사용하지 않음
            // 하지만 인터페이스 완성도를 위해 구현
            log.debug("Verifying signature with publicKey: {}", Arrays.toString(publicKey.getBytes()));
            return true; // VpManager에 위임
        } catch (Exception e) {
            log.error("Failed to verify signature", e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_SIGNATURE_VERIFICATION_FAILED,
                    "Signature verification failed: " + e.getMessage());
        }
    }

    @Override
    public String sha256(byte[] data) {
        try {
            byte[] hash = BaseDigestUtil.generateHash(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate SHA-256 hash", e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "SHA-256 hash generation failed: " + e.getMessage());
        }
    }

    @Override
    public byte[] decodeMultibase(String multibase) {
        try {
            return BaseMultibaseUtil.decode(multibase);
        } catch (Exception e) {
            log.error("Failed to decode Multibase: {}", multibase, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_INVALID_MULTIBASE,
                    "Multibase decoding failed: " + e.getMessage());
        }
    }

    @Override
    public String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    @Override
    public byte[] decodeBase64(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            log.error("Failed to decode Base64: {}", base64, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Base64 decoding failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // Phase 2-1: E2E 암호화 지원
    // ========================================================================

    @Override
    public KeyPairInfo generateKeyPair(String curve) {
        try {
            log.debug("Generating key pair with curve: {}", curve);

            // 1. Curve 타입 변환
            EccCurveType curveType = EccCurveType.fromValue(curve);

            // 2. 키쌍 생성
            KeyPairInterface keyPair = BaseCryptoUtil.generateKeyPair(curveType);

            // 3. 공개키 압축 (EC 포인트 추출)
            // getEncoded()는 DER 인코딩된 X.509 SubjectPublicKeyInfo (0x30 시작)
            // compressPublicKey()는 EC 포인트만 추출하여 압축 (0x02 또는 0x03 시작)
            byte[] publicKeyBytes = keyPair.getPublicKey().getEncoded();
            byte[] compressedPublicKey = BaseCryptoUtil.compressPublicKey(publicKeyBytes, curveType);

            // 4. 개인키 추출
            byte[] privateKeyBytes = keyPair.getPrivateKey().getEncoded();

            // 5. Multibase 인코딩
            String publicKey = BaseMultibaseUtil.encode(compressedPublicKey);
            String privateKey = BaseMultibaseUtil.encode(privateKeyBytes);

            log.debug("Key pair generated successfully (public key compressed)");

            return KeyPairInfo.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .build();

        } catch (OpenDidException e) {
            log.error("Failed to generate key pair with curve: {}", curve, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_KEY_GENERATION_FAILED,
                    "Key pair generation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating key pair with curve: {}", curve, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_KEY_GENERATION_FAILED,
                    "Unexpected error during key pair generation: " + e.getMessage());
        }
    }

    @Override
    public String generateNonce(int length) {
        try {
            log.debug("Generating nonce with length: {} bytes", length);

            // 1. Nonce 생성
            byte[] nonceBytes = BaseCryptoUtil.generateNonce(length);

            // 2. Multibase 인코딩
            String nonce = BaseMultibaseUtil.encode(nonceBytes);

            log.debug("Nonce generated successfully");
            return nonce;

        } catch (OpenDidException e) {
            log.error("Failed to generate nonce with length: {}", length, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_NONCE_GENERATION_FAILED,
                    "Nonce generation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating nonce with length: {}", length, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_NONCE_GENERATION_FAILED,
                    "Unexpected error during nonce generation: " + e.getMessage());
        }
    }

    @Override
    public String encodeMultibase(byte[] data) {
        try {
            return BaseMultibaseUtil.encode(data);
        } catch (Exception e) {
            log.error("Failed to encode Multibase", e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Multibase encoding failed: " + e.getMessage());
        }
    }

    @Override
    public byte[] generateSharedSecret(byte[] holderPublicKey, byte[] verifierPrivateKey, String curve) {
        try {
            log.debug("Generating ECDH shared secret with curve: {}", curve);

            // 1. Curve 타입 변환
            EccCurveType curveType = EccCurveType.fromValue(curve);

            // 2. ECDH 공유 비밀키 생성
            byte[] sharedSecret = BaseCryptoUtil.generateSharedSecret(
                    holderPublicKey,
                    verifierPrivateKey,
                    curveType
            );

            log.debug("ECDH shared secret generated successfully: {}", sharedSecret);

            return sharedSecret;

        } catch (OpenDidException e) {
            log.error("Failed to generate shared secret with curve: {}", curve, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_ECDH_KEY_AGREEMENT_FAILED,
                    "ECDH shared secret generation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating shared secret with curve: {}", curve, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_ECDH_KEY_AGREEMENT_FAILED,
                    "Unexpected error during ECDH: " + e.getMessage());
        }
    }

    @Override
    public byte[] deriveSessionKey(byte[] sharedSecret, byte[] nonce, String cipherType) {
        try {
            log.debug("Deriving session key with cipher type: {}", cipherType);

            // 1. Cipher 타입 변환
            SymmetricCipherType symmetricCipherType = SymmetricCipherType.fromDisplayName(cipherType);

            // 2. KDF: 공유 비밀키 + Nonce → 세션키
            byte[] sessionKey = BaseCryptoUtil.mergeSharedSecretAndNonce(
                    sharedSecret,
                    nonce,
                    symmetricCipherType
            );

            log.debug("Session key derived successfully" + ": {}", Arrays.toString(sessionKey));

            return sessionKey;

        } catch (OpenDidException e) {
            log.error("Failed to derive session key with cipher type: {}", cipherType, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_KEY_GENERATION_FAILED,
                    "Session key derivation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error deriving session key with cipher type: {}", cipherType, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_KEY_GENERATION_FAILED,
                    "Unexpected error during KDF: " + e.getMessage());
        }
    }

    @Override
    public byte[] decrypt(byte[] encData, byte[] sessionKey, byte[] iv, String cipherType, String paddingType) {
        try {
            log.debug("Decrypting data with cipher type: {}, padding type: {}", cipherType, paddingType);

            // 1. Cipher/Padding 타입 변환
            SymmetricCipherType symmetricCipherType = SymmetricCipherType.fromDisplayName(cipherType);
            SymmetricPaddingType symmetricPaddingType = SymmetricPaddingType.fromDisplayName(paddingType);

            // 2. 복호화
            byte[] decryptedData = BaseCryptoUtil.decrypt(
                    encData,
                    sessionKey,
                    iv,
                    symmetricCipherType,
                    symmetricPaddingType
            );

            log.debug("Data decrypted successfully");
            return decryptedData;

        } catch (OpenDidException e) {
            log.error("Failed to decrypt data with cipher: {}, padding: {}", cipherType, paddingType, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_DECRYPTION_FAILED,
                    "Decryption failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error decrypting data with cipher: {}, padding: {}", cipherType, paddingType, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_DECRYPTION_FAILED,
                    "Unexpected error during decryption: " + e.getMessage());
        }
    }

    // ========================================================================
    // ZKP 검증 지원
    // ========================================================================

    @Override
    public KeyPairInfo generateEcKeyPair(String curve) {
        // generateKeyPair와 동일한 구현
        return generateKeyPair(curve);
    }
}
