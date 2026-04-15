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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.SubTransactionStatus;
import org.omnione.did.base.db.constant.SubTransactionType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.Payload;
import org.omnione.did.base.db.domain.Policy;
import org.omnione.did.base.db.domain.SubTransaction;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.domain.VpOffer;
import org.omnione.did.base.db.repository.PayloadRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.common.util.JsonUtil;
import org.omnione.did.verifier.v1.agent.dto.RequestOfferReqDto;
import org.omnione.did.verifier.v1.agent.dto.RequestOfferResDto;
import org.omnione.did.verifier.v1.model.enums.OfferType;
import org.omnione.did.verifier.v1.model.enums.PresentMode;
import org.omnione.did.verifier.v1.model.data.VpOfferPayload;
import org.omnione.did.verifier.v1.common.PolicyCacheService;
import org.omnione.did.verifier.v1.protocol.VerifierService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * VP Offer 애플리케이션 서비스
 *
 * VP Offer QR 생성 관련 Application 비즈니스 로직을 담당합니다.
 *
 * 책임:
 * - Transaction 생성/저장
 * - VP Offer Payload 생성 (SDK 위임)
 * - VpOffer 엔티티 저장
 * - ZKP Offer Payload 직접 구성
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VpOfferApplicationService {

    private final VerifierService verifierService;
    private final TransactionService transactionService;
    private final VpOfferQueryService vpOfferQueryService;
    private final PolicyCacheService policyCacheService;
    private final PayloadRepository payloadRepository;
    private final ObjectMapper objectMapper;

    /**
     * VP Offer QR 생성
     *
     * @param requestOfferReqDto 요청 DTO
     * @return VP Offer 응답 DTO (txId, payload)
     */
    public RequestOfferResDto requestVpOfferbyQR(RequestOfferReqDto requestOfferReqDto) {
        log.debug("=== Starting requestVpOfferbyQR ===");

        try {
            String policyId = requestOfferReqDto.getPolicyId();

            // 1. Policy 조회 (cached)
            Policy policy = policyCacheService.findByPolicyId(policyId);

            // 2. Payload 조회
            Payload payload = payloadRepository.findByPayloadId(policy.getPayloadId())
                    .orElseThrow(() -> new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND));

            // 3. PolicyType과 OfferType 검증
            if (policy.getPolicyType() == org.omnione.did.base.db.constant.PolicyType.ZKP) {
                if (payload.getOfferType() != org.omnione.did.base.datamodel.enums.OfferType.VerifyProofOffer) {
                    log.error("ZKP Policy must use VerifyProofOffer type. Policy ID: {}, OfferType: {}",
                        policyId, payload.getOfferType());
                    throw new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND);
                }
            } else {
                if (payload.getOfferType() != org.omnione.did.base.datamodel.enums.OfferType.VerifyOffer) {
                    log.error("VP Policy must use VerifyOffer type. Policy ID: {}, OfferType: {}",
                        policyId, payload.getOfferType());
                    throw new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND);
                }
            }

            // 4. Transaction 생성
            Transaction transaction = createAndSaveTransaction();
            createAndSaveSubTransaction(transaction.getId());

            // 5. VP Offer Payload 생성
            VpOfferPayload offerPayload;
            if (policy.getPolicyType() == org.omnione.did.base.db.constant.PolicyType.ZKP) {
                offerPayload = createZkpOfferPayload(payload);
            } else {
                offerPayload = verifierService.requestVpOffer(
                        policyId,
                        payload.getDevice(),
                        payload.getService(),
                        payload.isLocked()
                );
            }

            // 6. VP Offer 저장
            saveVpOffer(transaction.getId(), offerPayload.getOfferId(), policyId, offerPayload);

            log.debug("*** Finished requestVpOfferbyQR ***");

            return RequestOfferResDto.builder()
                    .txId(transaction.getTxId())
                    .payload(offerPayload)
                    .build();

        } catch (OpenDidException e) {
            log.error("OpenDidException during requestVpOfferbyQR: {}", e.getErrorCode().getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Exception during requestVpOfferbyQR: {}", e.getMessage(), e);
            throw new OpenDidException(ErrorCode.FAILED_TO_REQUEST_OFFER_QR);
        }
    }

    private Transaction createAndSaveTransaction() {
        return transactionService.insertTransaction(Transaction.builder()
                .type(TransactionType.VP_SUBMIT)
                .txId(UUID.randomUUID().toString())
                .status(TransactionStatus.PENDING)
                .expired_at(transactionService.retrieveTransactionExpiredTime())
                .build());
    }

    private void createAndSaveSubTransaction(Long transactionId) {
        transactionService.saveSubTransaction(SubTransaction.builder()
                .transactionId(transactionId)
                .step(1)
                .type(SubTransactionType.REQUEST_OFFER)
                .status(SubTransactionStatus.COMPLETED)
                .build());
    }

    private void saveVpOffer(Long transactionId, String offerId, String policyId,
                             VpOfferPayload payload) {
        try {
            vpOfferQueryService.insertVpOffer(VpOffer.builder()
                    .transactionId(transactionId)
                    .offerId(offerId)
                    .device(payload.getDevice())
                    .service(payload.getService())
                    .vpPolicyId(policyId)
                    .offerType(payload.getType() != null ? payload.getType().toString() : null)
                    .payload(JsonUtil.serializeToJson(payload))
                    .validUntil(Instant.parse(payload.getValidUntil()))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save VP Offer", e);
            throw new OpenDidException(ErrorCode.FAILED_TO_REQUEST_OFFER_QR);
        }
    }

    /**
     * ZKP Offer Payload 직접 구성 (SDK 거치지 않음)
     * ZKP Policy는 Payload 정보만으로 Offer 생성 가능
     */
    private VpOfferPayload createZkpOfferPayload(Payload payload) {
        try {
            String offerId = UUID.randomUUID().toString();
            Instant validUntil = Instant.now().plusSeconds(payload.getValidSecond());

            List<String> endpoints = objectMapper.readValue(
                payload.getEndpoints(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            return VpOfferPayload.builder()
                .offerId(offerId)
                .type(OfferType.valueOf(payload.getOfferType().name()))
                .mode(PresentMode.fromDisplayName(payload.getMode().name()))
                .device(payload.getDevice())
                .service(payload.getService())
                .endpoints(endpoints)
                .validUntil(validUntil.toString())
                .locked(payload.isLocked())
                .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse endpoints from Payload", e);
            throw new OpenDidException(ErrorCode.VP_PAYLOAD_NOT_FOUND);
        }
    }
}
