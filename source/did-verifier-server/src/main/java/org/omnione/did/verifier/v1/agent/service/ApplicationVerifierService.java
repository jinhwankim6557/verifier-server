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
import com.fasterxml.jackson.core.JsonProcessingException;
import org.omnione.did.verifier.v1.agent.dto.*;


import java.util.HashMap;


/**
 * Application Verifier Service Interface
 * 
 * NOTE: 이름 변경됨 (VerifierService → ApplicationVerifierService)
 * 이유: SDK의 VerifierService와 이름 충돌 방지
 * 
 * Application 특화 로직을 위한 서비스 인터페이스
 * - VP 제출 처리
 * - 사용자 검증
 * - Transaction 관리 등
 */
public interface ApplicationVerifierService {

    /**
     * Requests a VP offer via QR code.
     *
     * @param requestOfferReqDto Request data for the VP offer.
     * @return VP offer response.
     */
    RequestOfferResDto requestVpOfferbyQR(RequestOfferReqDto requestOfferReqDto);

    /**
     * Requests user profile information.
     *
     * @param requestProfileReqDto Request data for the profile.
     * @return Profile response.
     */
    RequestProfileResDto requestProfile(RequestProfileReqDto requestProfileReqDto);

    /**
     * Requests claim verification.
     *
     * @param requestVerifyReqDto Request data for the verification.
     * @return Verification response.
     */
    RequestVerifyResDto requestVerify(RequestVerifyReqDto requestVerifyReqDto);

    /**
     * Confirms a verification result.
     *
     * @param confirmVerifyReqDto Request data for confirmation.
     * @return Confirmation response.
     */
    ConfirmVerifyResDto confirmVerify(ConfirmVerifyReqDto confirmVerifyReqDto);

    ProofRequestResDto requestProofRequestProfile(RequestProfileReqDto requestProfileReqDto);

    RequestVerifyResDto requestVerifyProof(RequestVerifyProofReqDto requestVerifyProofReqDto);


}
