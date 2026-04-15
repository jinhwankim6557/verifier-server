package org.omnione.did.verifier.v1.agent.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.vc.VcMeta;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.springframework.stereotype.Component;

/**
 * SDK StorageService 구현체 (Adapter 패턴)
 * 기존 Application의 StorageService를 SDK Interface에 맞게 연결
 *
 * 중요: DidDocument, VcMeta는 GsonWrapper 기반이므로
 * Jackson ObjectMapper 대신 toJson() 메서드를 사용해야 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageProviderImpl implements org.omnione.did.verifier.v1.provider.StorageProvider {

    private final org.omnione.did.verifier.v1.common.service.StorageService appStorageService;
    
    /**
     * DID로 DID Document 조회 (JSON 반환)
     *
     * @param did DID
     * @return DID Document (JSON 문자열)
     * @throws VerifierSdkException DID Document 미존재 또는 조회 실패 시
     */
    @Override
    public String findDidDocument(String did) {
        try {
            log.debug("Finding DID Document for DID: {}", did);

            // Application StorageService는 didKeyUrl을 받지만,
            // 여기서는 DID만 사용하므로 그대로 전달
            DidDocument didDoc = appStorageService.findDidDoc(did);

            if (didDoc == null) {
                log.warn("DID Document not found: {}", did);
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_DID_DOCUMENT_NOT_FOUND,
                        "DID Document not found: " + did);
            }

            // DidDocument 객체를 JSON 문자열로 변환 (GsonWrapper 사용)
            // IMPORTANT: ObjectMapper 대신 toJson() 사용 (@Expose 어노테이션 보존)
            String json = didDoc.toJson();
            log.debug("DID Document retrieved successfully for DID: {}", did);

            return json;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve DID Document for DID: {}", did, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_STORAGE_ERROR,
                    "Failed to retrieve DID Document: " + e.getMessage());
        }
    }
    
    /**
     * VC ID로 VC Meta 조회 (JSON 반환)
     *
     * @param vcId VC ID
     * @return VC Meta 정보 (JSON 문자열)
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public String getVcMeta(String vcId) {
        try {
            log.debug("Getting VC Meta for VC ID: {}", vcId);

            VcMeta vcMeta = appStorageService.getVcMeta(vcId);

            if (vcMeta == null) {
                log.warn("VC Meta not found: {}", vcId);
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_VC_META_NOT_FOUND,
                        "VC Meta not found: " + vcId);
            }

            // VcMeta 객체를 JSON 문자열로 변환 (GsonWrapper 사용)
            // IMPORTANT: ObjectMapper 대신 toJson() 사용 (@Expose 어노테이션 보존)
            String json = vcMeta.toJson();
            log.debug("VC Meta retrieved successfully for VC ID: {}", vcId);

            return json;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve VC Meta for VC ID: {}", vcId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_STORAGE_ERROR,
                    "Failed to retrieve VC Meta: " + e.getMessage());
        }
    }
    
    /**
     * DID Document 존재 여부 확인
     *
     * @param did DID
     * @return true: 존재, false: 미존재
     */
    @Override
    public boolean existsDidDocument(String did) {
        try {
            DidDocument didDoc = appStorageService.findDidDoc(did);
            return didDoc != null;
        } catch (Exception e) {
            log.debug("DID Document does not exist or error occurred: {}", did);
            return false;
        }
    }

    // ========================================================================
    // ZKP 검증 지원
    // ========================================================================

    /**
     * ZKP Credential Schema 조회
     *
     * @param schemaId Schema ID
     * @return org.omnione.did.zkp.datamodel.schema.CredentialSchema
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public org.omnione.did.zkp.datamodel.schema.CredentialSchema getZKPCredential(String schemaId) {
        try {
            log.debug("Retrieving ZKP Credential Schema for schemaId: {}", schemaId);

            // Application StorageService를 통해 조회
            org.omnione.did.zkp.datamodel.schema.CredentialSchema schema =
                appStorageService.getZKPCredential(schemaId);

            if (schema == null) {
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_ZKP_CREDENTIAL_NOT_FOUND,
                        "ZKP Credential Schema not found: " + schemaId);
            }

            return schema;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve ZKP Credential Schema for schemaId: {}", schemaId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_STORAGE_ERROR,
                    "Failed to retrieve ZKP Credential Schema: " + e.getMessage());
        }
    }

    /**
     * ZKP Credential Definition 조회
     *
     * @param credDefId Credential Definition ID
     * @return org.omnione.did.zkp.datamodel.definition.CredentialDefinition
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public org.omnione.did.zkp.datamodel.definition.CredentialDefinition getZKPCredentialDefinition(String credDefId) {
        try {
            log.debug("Retrieving ZKP Credential Definition for credDefId: {}", credDefId);

            // Application StorageService를 통해 조회
            org.omnione.did.zkp.datamodel.definition.CredentialDefinition credDef =
                appStorageService.getZKPCredentialDefinition(credDefId);

            if (credDef == null) {
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_ZKP_CREDENTIAL_DEF_NOT_FOUND,
                        "ZKP Credential Definition not found: " + credDefId);
            }

            return credDef;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve ZKP Credential Definition for credDefId: {}", credDefId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_STORAGE_ERROR,
                    "Failed to retrieve ZKP Credential Definition: " + e.getMessage());
        }
    }
}
