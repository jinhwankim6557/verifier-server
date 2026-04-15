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
import org.omnione.did.base.datamodel.enums.ProofPurpose;
import org.omnione.did.base.db.domain.VerifierInfo;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.util.BaseCoreDidUtil;
import org.omnione.did.base.util.BaseMultibaseUtil;
import org.omnione.did.common.exception.CommonSdkException;
import org.omnione.did.common.util.DateTimeUtil;
import org.omnione.did.common.util.JsonUtil;
import org.omnione.did.crypto.enums.MultiBaseType;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.did.Proof;
import org.omnione.did.data.model.did.VerificationMethod;
import org.omnione.did.data.model.enums.did.ProofType;
import org.omnione.did.data.model.profile.verify.VerifyProfile;
import org.omnione.did.verifier.v1.admin.service.VerifierInfoQueryService;
import org.omnione.did.verifier.v1.model.data.ProofRequestProfile;
import org.springframework.stereotype.Service;

/**
 * Proof 서명 서비스
 *
 * Application Server의 FileWallet을 사용하여 Proof를 생성합니다.
 *
 * 설계 원칙:
 * - Proof 생성은 Application Server 책임 (서명 키가 FileWallet에 있음)
 * - SDK는 Proof 없는 Profile을 반환하고, Application이 Proof 추가
 *
 * 주요 기능:
 * - generatePreProof: Proof 메타데이터 생성 (서명값 없음)
 * - generateProof: VerifyProfile에 서명 생성
 * - generateZkpProof: ZKP ProofRequestProfile에 서명 생성
 * - signVerifyProfile: VerifyProfile Proof 생성 편의 메서드 (preProof + proof)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProofSigningService {

    private final FileWalletService walletService;
    private final DidDocService didDocService;
    private final VerifierInfoQueryService verifierInfoQueryService;

    /**
     * VerifyProfile에 Proof 서명 추가 (편의 메서드)
     * generatePreProof + generateProof를 하나로 합친 메서드
     *
     * @param verifyProfile Proof를 추가할 VerifyProfile
     */
    public void signVerifyProfile(VerifyProfile verifyProfile) {
        VerifierInfo verifierInfo =
                verifierInfoQueryService.getVerifierInfo();
        DidDocument verifierDidDoc = didDocService.getDidDocument(verifierInfo.getDid());
        verifyProfile.setProof(generatePreProof(verifierDidDoc));
        verifyProfile.setProof(generateProof(verifyProfile));
    }

    /**
     * Proof 메타데이터 생성 (서명값 없음)
     * VerifyProfile의 서명 전 단계 - 검증 메서드, 타임스탬프 등 설정
     *
     * @param verifierDidDoc Verifier DID Document
     * @return Proof 메타데이터 (proofValue 없음)
     */
    public Proof generatePreProof(DidDocument verifierDidDoc) {
        Proof proof = new Proof();
        proof.setType(ProofType.SECP256R1_SIGNATURE_2018.getRawValue());
        proof.setCreated(DateTimeUtil.getCurrentUTCTimeString());
        proof.setProofPurpose(ProofPurpose.ASSERTION_METHOD.toString());
        proof.setVerificationMethod(getVerificationMethod(verifierDidDoc));
        return proof;
    }

    /**
     * VerifyProfile에 서명 생성
     * 직렬화 후 FileWallet으로 서명하여 proofValue 설정
     *
     * @param verifyProfile 서명할 VerifyProfile (preProof가 설정된 상태)
     * @return 서명이 포함된 Proof
     */
    public Proof generateProof(VerifyProfile verifyProfile) {
        try {
            String serializedAndSortedProfile = JsonUtil.serializeAndSort(verifyProfile);
            byte[] signatureBytes = walletService.generateCompactSignature("assert", serializedAndSortedProfile);
            Proof proof = new Proof();
            proof.setType(verifyProfile.getProof().getType());
            proof.setCreated(verifyProfile.getProof().getCreated());
            proof.setProofPurpose(verifyProfile.getProof().getProofPurpose());
            proof.setVerificationMethod(verifyProfile.getProof().getVerificationMethod());
            proof.setProofValue(BaseMultibaseUtil.encode(signatureBytes, MultiBaseType.base58btc));
            return proof;
        } catch (CommonSdkException e) {
            throw new OpenDidException(ErrorCode.JSON_PARSE_ERROR);
        }
    }

    /**
     * ZKP ProofRequestProfile에 서명 생성
     *
     * @param profile 서명할 ProofRequestProfile
     * @param preProof Proof 메타데이터
     * @return 서명이 포함된 Proof
     */
    public Proof generateZkpProof(ProofRequestProfile profile, Proof preProof) {
        try {
            String serializedAndSortedProfile = JsonUtil.serializeAndSort(profile);
            byte[] signatureBytes = walletService.generateCompactSignature("assert", serializedAndSortedProfile);
            Proof proof = new Proof();
            proof.setType(preProof.getType());
            proof.setCreated(preProof.getCreated());
            proof.setProofPurpose(preProof.getProofPurpose());
            proof.setVerificationMethod(preProof.getVerificationMethod());
            proof.setProofValue(BaseMultibaseUtil.encode(signatureBytes, MultiBaseType.base58btc));
            return proof;
        } catch (CommonSdkException e) {
            throw new OpenDidException(ErrorCode.JSON_PARSE_ERROR);
        }
    }

    /**
     * ZKP ProofRequestProfile에 Proof 서명 추가 (편의 메서드)
     *
     * @param sdkProfile Proof를 추가할 ProofRequestProfile
     */
    public void signZkpProfile(ProofRequestProfile sdkProfile) {
        VerifierInfo verifierInfo =
                verifierInfoQueryService.getVerifierInfo();
        DidDocument verifierDidDoc = didDocService.getDidDocument(verifierInfo.getDid());
        Proof preProof = generatePreProof(verifierDidDoc);
        sdkProfile.setProof(generateZkpProof(sdkProfile, preProof));
    }

    /**
     * VerificationMethod ID 추출
     * DID Document에서 assertionMethod 키의 verificationMethod ID를 반환
     *
     * @param verifierDidDoc Verifier DID Document
     * @return verificationMethod ID (DID#key 형식)
     */
    public String getVerificationMethod(DidDocument verifierDidDoc) {
        String version = verifierDidDoc.getVersionId();
        VerificationMethod verificationMethod = BaseCoreDidUtil.getVerificationMethod(
                verifierDidDoc, ProofPurpose.ASSERTION_METHOD.toKeyId());
        return verifierDidDoc.getId() + "?versionId=" + version + "#" + verificationMethod.getId();
    }
}
