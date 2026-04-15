package org.omnione.did.verifier.v1.common.service;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.did.VerificationMethod;
import org.omnione.did.data.model.enums.did.ProofPurpose;
import org.omnione.did.data.model.enums.did.ProofType;
import org.omnione.did.data.model.vc.VcMeta;
import org.omnione.did.zkp.datamodel.definition.CredentialDefinition;
import org.omnione.did.zkp.datamodel.schema.CredentialSchema;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@Profile({"sample", "test"})
public class MockStorageServiceImpl implements StorageService {

    @Override
    public DidDocument findDidDoc(String didKeyUrl) {
        log.debug("MockStorageServiceImpl: Creating mock DID Document for: {}", didKeyUrl);

        // 테스트용 간단한 DID Document 생성
        DidDocument didDoc = new DidDocument();
        didDoc.setId(didKeyUrl);
        didDoc.setContext(Arrays.asList("https://www.w3.org/ns/did/v1"));
        didDoc.setController(didKeyUrl);

        // 필수 필드 추가
        didDoc.setVersionId("1");
        didDoc.setCreated("2024-01-01T00:00:00Z");
        didDoc.setUpdated("2024-01-01T00:00:00Z");

        // VerificationMethod 추가 (필수)
        VerificationMethod authMethod = new VerificationMethod();
        authMethod.setId("auth");  // fragment만 사용
        authMethod.setType(ProofType.SECP256R1_SIGNATURE_2018.toString());
        authMethod.setController(didKeyUrl);
        authMethod.setPublicKeyMultibase("zMockPublicKey12345");
        authMethod.setAuthType(1);  // 필수: authType 설정

        VerificationMethod assertMethod = new VerificationMethod();
        assertMethod.setId("assert");  // fragment만 사용
        assertMethod.setType(ProofType.SECP256R1_SIGNATURE_2018.toString());
        assertMethod.setController(didKeyUrl);
        assertMethod.setPublicKeyMultibase("zMockPublicKey67890");
        assertMethod.setAuthType(1);  // 필수: authType 설정

        didDoc.setVerificationMethod(Arrays.asList(authMethod, assertMethod));

        // Proof Purpose 설정 - fragment만 사용
        didDoc.setAssertionMethod(Arrays.asList("assert"));
        didDoc.setAuthentication(Arrays.asList("auth"));

        log.debug("MockStorageServiceImpl: Created mock DID Document successfully");
        return didDoc;
    }

    @Override
    public CredentialSchema getZKPCredential(String credentialSchemaId) {
        throw new UnsupportedOperationException("ZKP credential lookup is not implemented in mock storage.");
    }

    @Override
    public CredentialDefinition getZKPCredentialDefinition(String credentialDefinitionId) {
        throw new UnsupportedOperationException("ZKP credential definition lookup is not implemented in mock storage.");
    }

    @Override
    public VcMeta getVcMeta(String vcId) {
        throw new UnsupportedOperationException("VC meta lookup is not implemented in mock storage.");
    }
}
