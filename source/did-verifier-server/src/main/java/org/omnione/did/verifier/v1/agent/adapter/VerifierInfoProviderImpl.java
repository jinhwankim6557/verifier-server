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
import org.omnione.did.base.db.domain.VerifierInfo;
import org.omnione.did.base.property.VerifierProperty;
import org.omnione.did.data.model.provider.ProviderDetail;
import org.omnione.did.verifier.v1.admin.service.VerifierInfoQueryService;
import org.omnione.did.verifier.v1.provider.VerifierInfoProvider;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.omnione.did.verifier.v1.common.service.StorageService;
import org.springframework.stereotype.Component;

/**
 * VerifierInfoProvider의 구현체 (Adapter 패턴)
 *
 * 목적:
 * - Admin 설정값(DB)으로 저장된 Verifier 정보를 SDK Interface로 제공
 * - StorageService를 통해 Verifier DID Document 조회
 *
 * 설계 원칙:
 * - Core 라이브러리 타입(ProviderDetail)을 직접 반환
 * - 어댑터는 변환만 담당 (비즈니스 로직 없음)
 *
 * 의존성:
 * - VerifierInfoQueryService: DB(verifier 테이블)에 저장된 Admin 설정값 조회
 * - VerifierProperty: application.yml의 verifier.* 설정 (DID Document 조회 보조용)
 * - StorageService: DID Document 조회용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifierInfoProviderImpl implements VerifierInfoProvider {

    private final VerifierInfoQueryService verifierInfoQueryService;
    private final VerifierProperty verifierProperty;
    private final StorageService storageService;

    /**
     * Verifier 기본 정보 조회
     *
     * Admin 설정값(DB의 verifier 테이블)을 기준으로 구성합니다.
     * 매핑 규칙은 Admin 화면 표시 경로(PolicyProfileService)와 동일합니다.
     *
     * @return ProviderDetail (Core, DID, 이름, certVcRef, 참조 URL 등)
     * @throws VerifierSdkException Verifier 설정이 없거나 불완전한 경우
     */
    @Override
    public ProviderDetail getVerifierInfo() {
        log.debug("=== VerifierInfoProviderAdapter.getVerifierInfo 시작 ===");

        try {
            // Admin 설정값(DB) 조회 (application-verifier.yml 미참조)
            VerifierInfo verifierInfo = verifierInfoQueryService.getVerifierInfo();

            String did = verifierInfo.getDid();
            String name = verifierInfo.getName();

            if (did == null || did.isBlank()) {
                log.error("Verifier DID가 설정되지 않았습니다.");
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_VERIFIER_INFO_NOT_FOUND,
                        "Verifier DID is not configured (DB)");
            }

            if (name == null || name.isBlank()) {
                log.warn("Verifier name이 설정되지 않았습니다. DID를 name으로 사용합니다.");
            }

            // Core ProviderDetail 구성 (Admin 화면과 동일한 매핑: PolicyProfileService 참조)
            ProviderDetail providerDetail = new ProviderDetail();
            providerDetail.setDid(did);
            providerDetail.setName(name != null && !name.isBlank() ? name : did);
            providerDetail.setCertVcRef(verifierInfo.getCertificateUrl());
            providerDetail.setRef(verifierInfo.getServerUrl());

            log.debug("Verifier 정보 조회 성공(DB): DID={}, Name={}", did, name);
            return providerDetail;

        } catch (VerifierSdkException e) {
            log.error("Verifier 정보 조회 중 설정 오류 발생", e);
            throw e;
        } catch (Exception e) {
            log.error("Verifier 정보 조회 중 예상치 못한 오류 발생", e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_CONFIGURATION_ERROR,
                    "Failed to get Verifier info: " + e.getMessage());
        }
    }

    /**
     * Verifier DID Document 조회
     *
     * @return DID Document (JSON 문자열)
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public String getVerifierDidDocument() {
        log.debug("=== VerifierInfoProviderAdapter.getVerifierDidDocument 시작 ===");

        try {
            String verifierDid = verifierProperty.getDid();

            if (verifierDid == null || verifierDid.isBlank()) {
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_VERIFIER_INFO_NOT_FOUND,
                        "Verifier DID is not configured");
            }

            org.omnione.did.data.model.did.DidDocument didDoc = storageService.findDidDoc(verifierDid);
            String didDocument = didDoc != null ? didDoc.toJson() : null;

            log.debug("Verifier DID Document 조회 성공: DID={}", verifierDid);
            return didDocument;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Verifier DID Document 조회 중 오류 발생", e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_DID_DOCUMENT_NOT_FOUND,
                    "Failed to get Verifier DID Document: " + e.getMessage());
        }
    }

    /**
     * Verifier DID 조회
     *
     * @return Verifier DID
     * @throws VerifierSdkException DID가 설정되지 않은 경우
     */
    @Override
    public String getVerifierDid() {
        String did = verifierProperty.getDid();

        if (did == null || did.isBlank()) {
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_VERIFIER_INFO_NOT_FOUND,
                    "Verifier DID is not configured in application.yml");
        }

        return did;
    }
}
