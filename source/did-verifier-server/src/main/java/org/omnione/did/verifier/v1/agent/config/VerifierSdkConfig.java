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

package org.omnione.did.verifier.v1.agent.config;

import org.omnione.did.verifier.v1.config.VerifierSdkBuilder;
import org.omnione.did.verifier.v1.provider.*;
import org.omnione.did.verifier.v1.protocol.VerifierService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SDK VerifierService Bean Configuration
 *
 * SDK의 VerifierService를 Spring Bean으로 등록합니다.
 * 7개의 Interface 구현체(Adapter)를 주입받아 VerifierService를 생성합니다.
 */
@Configuration
public class VerifierSdkConfig {

    /**
     * SDK VerifierService Bean
     *
     * @param configProvider         Policy/Profile 조회 Provider
     * @param verifierInfoProvider   Verifier 정보 Provider
     * @param sessionProvider        E2E 세션 Provider
     * @param storageService         DID Document/VC Meta 조회 Service
     * @param transactionManager     Transaction 관리자
     * @param nonceGenerator         Nonce 생성기
     * @param cryptoHelper           암호화 헬퍼
     * @return VerifierService (SDK Facade)
     */
    @Bean
    public VerifierService verifierService(
            VerificationConfigProvider configProvider,
            VerifierInfoProvider verifierInfoProvider,
            EcdhSessionProvider sessionProvider,
            StorageProvider storageService,
            TransactionProvider transactionManager,
            NonceGenerator nonceGenerator,
            CryptoHelper cryptoHelper
    ) {
        return new VerifierSdkBuilder()
                .configProvider(configProvider)
                .verifierInfoProvider(verifierInfoProvider)
                .sessionProvider(sessionProvider)
                .storageProvider(storageService)
                .transactionProvider(transactionManager)
                .nonceGenerator(nonceGenerator)
                .cryptoHelper(cryptoHelper)
                .build();
    }
}
