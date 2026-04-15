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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * E2E 암호화 관련 설정
 * application.yml의 e2e 섹션 매핑
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "e2e")
public class E2eProperty {
    /**
     * ECC Curve Type
     * 기본값: Secp256r1
     */
    private String curve = "Secp256r1";

    /**
     * 대칭키 암호화 알고리즘
     * 기본값: AES-256-CBC
     */
    private String cipher = "AES-256-CBC";

    /**
     * 패딩 방식
     * 기본값: PKCS5
     */
    private String padding = "PKCS5";
}
