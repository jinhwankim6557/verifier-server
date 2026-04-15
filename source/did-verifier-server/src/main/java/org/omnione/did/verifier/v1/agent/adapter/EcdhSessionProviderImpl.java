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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.E2e;
import org.omnione.did.base.db.repository.E2eRepository;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.verifier.v1.agent.config.E2eProperty;
import org.omnione.did.verifier.v1.agent.service.E2EQueryService;
import org.omnione.did.verifier.v1.provider.CryptoHelper;
import org.omnione.did.verifier.v1.provider.EcdhSessionProvider;
import org.omnione.did.verifier.v1.provider.TransactionProvider;
import org.omnione.did.verifier.v1.model.data.KeyPairInfo;
import org.omnione.did.verifier.v1.model.data.ReqE2e;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * E2E 세션 제공자 어댑터
 * 
 * SDK의 EcdhSessionProvider 인터페이스를 구현하여
 * Application의 E2E 세션 관리 로직을 SDK에 연결합니다.
 * 
 * 주요 기능:
 * - E2E 세션 생성 (키쌍 생성, nonce 생성, DB 저장)
 * - E2E 세션 조회
 * - E2E 세션 삭제
 * - 암호화된 데이터 복호화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EcdhSessionProviderImpl implements EcdhSessionProvider {

    private final E2EQueryService e2eQueryService;
    private final E2eRepository e2eRepository;
    private final TransactionProvider transactionManager;
    private final E2eProperty e2eProperty;
    private final CryptoHelper cryptoHelper;

    /**
     * E2E 세션 저장 (ZKP용)
     *
     * ZKP ProofRequestProfile 생성 시 E2E 키쌍과 설정을 저장합니다.
     *
     * @param txId Transaction ID
     * @param keyPair E2E 키쌍 (publicKey, privateKey, curve)
     * @param reqE2e E2E 암호화 설정 (nonce, cipher, padding)
     * @throws VerifierSdkException 저장 실패 시
     */
    @Override
    public void saveSession(String txId, KeyPairInfo keyPair, ReqE2e reqE2e) {
        try {
            log.debug("Saving E2E session for txId: {}", txId);

            // 1. Transaction ID 조회
            Long transactionId = transactionManager.getTransactionId(txId);
            log.debug("Found transactionId: {} for txId: {}", transactionId, txId);

            // 2. E2e Entity 생성 및 저장
            E2e e2e = E2e.builder()
                    .transactionId(transactionId)
                    .sessionKey(keyPair.getPrivateKey())  // privateKey를 sessionKey로 저장
                    .nonce(reqE2e.getNonce())
                    .curve(reqE2e.getCurve())
                    .cipher(reqE2e.getCipher())
                    .padding(reqE2e.getPadding())
                    .build();

            e2eQueryService.save(e2e);
            log.info("E2E session saved successfully for txId: {}", txId);

        } catch (OpenDidException e) {
            log.error("Failed to save E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Failed to save E2E session: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error saving E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Unexpected error saving E2E session: " + e.getMessage());
        }
    }

    /**
     * 새로운 E2E 세션 생성
     *
     * @param txId Transaction ID
     * @return ReqE2e (nonce, curve, publicKey, cipher, padding)
     * @throws VerifierSdkException 생성 실패 시
     */
    @Override
    public ReqE2e createSession(String txId) {
        try {
            log.debug("Creating E2E session for txId: {}", txId);

            // 1. Transaction ID 조회
            Long transactionId = transactionManager.getTransactionId(txId);
            log.debug("Found transactionId: {} for txId: {}", transactionId, txId);

            // 2. 키쌍 생성 (CryptoHelper 사용)
            KeyPairInfo keyPair = cryptoHelper.generateKeyPair(e2eProperty.getCurve());
            log.debug("Generated key pair with curve: {}", e2eProperty.getCurve());

            // 3. Nonce 생성 (CryptoHelper 사용, 16 bytes)
            String nonce = cryptoHelper.generateNonce(16);
            log.debug("Generated nonce");

            // 4. E2e Entity 생성 및 저장 (privateKey를 sessionKey로 저장)
            E2e e2e = E2e.builder()
                    .transactionId(transactionId)
                    .sessionKey(keyPair.getPrivateKey())  // 이미 Multibase 인코딩됨
                    .nonce(nonce)                          // 이미 Multibase 인코딩됨
                    .curve(e2eProperty.getCurve())
                    .cipher(e2eProperty.getCipher())
                    .padding(e2eProperty.getPadding())
                    .build();

            e2eQueryService.save(e2e);
            log.info("E2E session created successfully for txId: {}", txId);

            // 5. ReqE2e 반환
            return ReqE2e.builder()
                    .nonce(nonce)
                    .curve(e2eProperty.getCurve())
                    .publicKey(keyPair.getPublicKey())  // 이미 Multibase 인코딩됨
                    .cipher(e2eProperty.getCipher())
                    .padding(e2eProperty.getPadding())
                    .build();

        } catch (OpenDidException e) {
            log.error("Failed to create E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Failed to create E2E session: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_UNKNOWN_ERROR,
                    "Unexpected error creating E2E session: " + e.getMessage());
        }
    }

    /**
     * E2E 세션 조회
     *
     * @param txId Transaction ID
     * @return ReqE2e (nonce, curve, publicKey, cipher, padding)
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public ReqE2e getSession(String txId) {
        try {
            log.debug("Getting E2E session for txId: {}", txId);

            // 1. Transaction ID 조회
            Long transactionId = transactionManager.getTransactionId(txId);

            // 2. E2E 조회
            E2e e2e = e2eQueryService.findByTransactionId(transactionId);
            log.debug("Found E2E session for transactionId: {}", transactionId);

            // 3. ReqE2e 변환 반환
            // Note: sessionKey는 privateKey이므로 publicKey로 변환 필요
            // BaseCryptoUtil.compressPublicKey는 privateKey에서 publicKey를 복원
            // 이 부분은 CryptoHelper에 추가하지 않고 기존 유틸 사용 (레거시 호환)
            byte[] privateKeyBytes = cryptoHelper.decodeMultibase(e2e.getSessionKey());
            byte[] publicKeyBytes = org.omnione.did.base.util.BaseCryptoUtil.compressPublicKey(
                    privateKeyBytes,
                    org.omnione.did.base.datamodel.enums.EccCurveType.fromValue(e2e.getCurve())
            );
            String publicKey = cryptoHelper.encodeMultibase(publicKeyBytes);

            return ReqE2e.builder()
                    .nonce(e2e.getNonce())
                    .curve(e2e.getCurve())
                    .publicKey(publicKey)
                    .cipher(e2e.getCipher())
                    .padding(e2e.getPadding())
                    .build();

        } catch (OpenDidException e) {
            log.error("Failed to get E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_E2E_SESSION_NOT_FOUND,
                    "Failed to get E2E session: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error getting E2E session for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_E2E_SESSION_NOT_FOUND,
                    "Unexpected error getting E2E session: " + e.getMessage());
        }
    }

    /**
     * E2E 세션 삭제
     * 
     * @param txId Transaction ID
     */
    @Override
    public void removeSession(String txId) {
        try {
            log.debug("Removing E2E session for txId: {}", txId);
            
            // 1. Transaction ID 조회
            Long transactionId = transactionManager.getTransactionId(txId);
            
            // 2. E2E 조회 및 삭제
            E2e e2e = e2eQueryService.findByTransactionId(transactionId);
            e2eRepository.delete(e2e);
            
            log.info("E2E session removed successfully for txId: {}", txId);
            
        } catch (OpenDidException e) {
            log.warn("Failed to remove E2E session for txId: {}", txId, e);
            // 삭제 실패는 예외를 던지지 않음 (이미 없을 수 있음)
        } catch (Exception e) {
            log.warn("Unexpected error removing E2E session for txId: {}", txId, e);
        }
    }

    /**
     * E2E 세션 존재 여부 확인
     * 
     * @param txId Transaction ID
     * @return true: 존재, false: 미존재
     */
    @Override
    public boolean existsSession(String txId) {
        try {
            log.debug("Checking E2E session existence for txId: {}", txId);
            
            // 1. Transaction ID 조회
            Long transactionId = transactionManager.getTransactionId(txId);
            
            // 2. E2E 존재 여부 확인
            boolean exists = e2eRepository.findByTransactionId(transactionId).isPresent();
            log.debug("E2E session exists for txId {}: {}", txId, exists);
            
            return exists;
            
        } catch (Exception e) {
            log.debug("E2E session does not exist for txId: {}", txId);
            return false;
        }
    }

    /**
     * 암호화된 데이터 복호화 (ECDH 기반 E2E 복호화)
     *
     * E2E 복호화 프로토콜:
     * 1. Holder 공개키 추출 (Multibase 디코딩)
     * 2. ECDH: Verifier 개인키 + Holder 공개키 → 공유 비밀키 생성
     * 3. 세션키 = KDF(공유 비밀키 + nonce)
     * 4. 평문 VP = Decrypt(encVp, 세션키, iv)
     *
     * 주의: encHolderPublicKey는 암호화되지 않은 공개키입니다 (Multibase 인코딩만)
     *
     * @param txId Transaction ID
     * @param encHolderPublicKey Holder 공개키 (Multibase 인코딩)
     * @param encVp 암호화된 VP (Multibase 인코딩)
     * @param iv IV (Multibase 인코딩)
     * @return 복호화된 VP (JSON 문자열)
     * @throws VerifierSdkException 복호화 실패 시
     */
    @Override
    public String decrypt(String txId, String encHolderPublicKey, String encVp, String iv) {
        try {
            log.debug("Decrypting E2E VP for txId: {}", txId);

            // 1. E2E 세션 조회
            Long transactionId = transactionManager.getTransactionId(txId);
            E2e e2e = e2eQueryService.findByTransactionId(transactionId);

            // 2. 암호화 설정 추출 (CryptoHelper 사용)
            byte[] verifierPrivateKeyBytes = cryptoHelper.decodeMultibase(e2e.getSessionKey());
            byte[] nonceBytes = cryptoHelper.decodeMultibase(e2e.getNonce());
            byte[] ivBytes = cryptoHelper.decodeMultibase(iv);

            // 3. Holder 공개키 추출
            // ECDH 프로토콜에서는 공개키가 그대로 전송됨 (암호화되지 않음)
            // AccE2e.publicKey는 Holder의 공개키를 Multibase로 인코딩한 값
            byte[] holderPublicKeyBytes = cryptoHelper.decodeMultibase(encHolderPublicKey);
            log.debug("Extracted Holder public key");

            // 4. ECDH 공유 비밀키 생성 (CryptoHelper 사용)
            byte[] sharedSecret = cryptoHelper.generateSharedSecret(
                    holderPublicKeyBytes,
                    verifierPrivateKeyBytes,
                    e2e.getCurve()
            );
            log.debug("Generated ECDH shared secret");

            // 5. 세션키 생성 - KDF (CryptoHelper 사용)
            byte[] sessionKey = cryptoHelper.deriveSessionKey(
                    sharedSecret,
                    nonceBytes,
                    e2e.getCipher()
            );
            log.debug("Generated session key from shared secret and nonce");

            // 6. VP 복호화 (CryptoHelper 사용)
            byte[] encVpBytes = cryptoHelper.decodeMultibase(encVp);
            byte[] decryptedVpBytes = cryptoHelper.decrypt(
                    encVpBytes,
                    sessionKey,
                    ivBytes,
                    e2e.getCipher(),
                    e2e.getPadding()
            );

            String vpJson = new String(decryptedVpBytes, StandardCharsets.UTF_8);
            log.info("E2E VP decrypted successfully for txId: {}", txId);

            return vpJson;

        } catch (OpenDidException e) {
            log.error("Failed to decrypt E2E VP for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_DECRYPTION_FAILED,
                    "Failed to decrypt E2E VP: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error decrypting E2E VP for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_DECRYPTION_FAILED,
                    "Unexpected error decrypting E2E VP: " + e.getMessage());
        }
    }
}
