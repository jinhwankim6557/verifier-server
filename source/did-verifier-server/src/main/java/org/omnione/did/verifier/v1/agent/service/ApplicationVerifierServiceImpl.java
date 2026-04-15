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

package org.omnione.did.verifier.v1.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.verifier.v1.agent.dto.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * ApplicationVerifierService Facade 구현체
 *
 * 4개 기능별 서비스로 순수 위임합니다.
 * 비즈니스 로직은 각 서비스에서 처리합니다.
 *
 * 구조 변경 내역:
 * - 이전: 1,020줄, 비즈니스/변환/DB 저장 혼재
 * - 이후: ~50줄, 4개 서비스로 위임
 *
 * 삭제된 코드:
 * - convertToAppProfile() 130줄: SDK가 Core VerifyProfile 직접 반환으로 불필요
 * - Filter 변환 코드: Core Filter를 SDK에 직접 전달로 불필요 (Gson/GsonWrapper 3단계 제거)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!sample")
public class ApplicationVerifierServiceImpl implements ApplicationVerifierService {

    private final VpOfferApplicationService offerService;
    private final VpProfileApplicationService profileService;
    private final VpVerificationApplicationService verificationService;
    private final VpConfirmApplicationService confirmService;

    @Override
    public RequestOfferResDto requestVpOfferbyQR(RequestOfferReqDto requestOfferReqDto) {
        return offerService.requestVpOfferbyQR(requestOfferReqDto);
    }

    @Override
    public RequestProfileResDto requestProfile(RequestProfileReqDto requestProfileReqDto) {
        return profileService.requestProfile(requestProfileReqDto);
    }

    @Override
    public RequestVerifyResDto requestVerify(RequestVerifyReqDto requestVerifyReqDto) {
        return verificationService.requestVerify(requestVerifyReqDto);
    }

    @Override
    public ConfirmVerifyResDto confirmVerify(ConfirmVerifyReqDto confirmVerifyReqDto) {
        return confirmService.confirmVerify(confirmVerifyReqDto);
    }

    @Override
    public ProofRequestResDto requestProofRequestProfile(RequestProfileReqDto requestProfileReqDto) {
        return profileService.requestProofRequestProfile(requestProfileReqDto);
    }

    @Override
    public RequestVerifyResDto requestVerifyProof(RequestVerifyProofReqDto requestVerifyProofReqDto) {
        return verificationService.requestVerifyProof(requestVerifyProofReqDto);
    }
}
