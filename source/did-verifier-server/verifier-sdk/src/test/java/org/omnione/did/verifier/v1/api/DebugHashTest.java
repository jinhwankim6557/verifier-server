package org.omnione.did.verifier.v1.api;

import org.junit.jupiter.api.Test;
import org.omnione.did.data.model.vp.VerifiablePresentation;
import org.omnione.did.data.model.vp.VpProof;

public class DebugHashTest {
    @Test
    public void testHash() {
        String json = "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],\"holder\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD\",\"id\":\"ccf86934-7ba4-41ec-bc70-dfad512784da\",\"proof\":{\"created\":\"2026-04-13T07:14:29.000Z\",\"proofPurpose\":\"authentication\",\"proofValue\":\"z3u6Ym3YoXJCw94cLMP2MRLxtnHRBy5Q5YJZbdH1sSNAUsNYR2F3qrrLu8AY47Lk1PKUh7xgWtL99pHjVsJ8SbWDwG\",\"type\":\"Secp256r1Signature2018\",\"verificationMethod\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD?versionId=1#auth\"},\"type\":[\"VerifiablePresentation\"],\"validFrom\":\"2026-04-13T07:14:29.000Z\",\"validUntil\":\"2026-04-14T07:14:29.000Z\",\"verifiableCredential\":[{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],\"credentialSchema\":{\"id\":\"http://localhost:8091/issuer/api/v1/vc/vcschema?name=Test1234Schenm\",\"type\":\"OsdSchemaCredential\"},\"credentialSubject\":{\"claims\":[{\"caption\":\"Name\",\"code\":\"Test1234.name\",\"format\":\"plain\",\"hideValue\":false,\"type\":\"text\",\"value\":\"Gildong Hong\"}],\"id\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD\"},\"id\":\"2ed678e8-abdb-447e-b1a6-44c32aa24a46\",\"issuer\":{\"id\":\"did:omn:issuer\",\"name\":\"OpenDID University\"},\"proof\":{\"created\":\"2026-01-01T00:00:00Z\",\"proofPurpose\":\"assertionMethod\",\"proofValue\":\"z3oqhA4Ry6vG23X6HFomPven4BctzfUfzv28hCdz6GHuVhAQnDNthJ6YvSrzP1DvoJeTLVB8st1bMiuvDa9CKXAKmL\",\"type\":\"Secp256r1Signature2018\",\"verificationMethod\":\"did:omn:issuer?versionId=1#assert\"},\"type\":[\"VerifiableCredential\"],\"validFrom\":\"2026-04-13T07:14:29.000Z\",\"validUntil\":\"2027-04-13T07:14:29.000Z\"}],\"verifierNonce\":\"m5jP6b/Ey30e77131Z06iYA\"}";
        try {
            VerifiablePresentation vp = new VerifiablePresentation();
            vp.fromJson(json);

            VerifiablePresentation tmpVerifiablePresentation = new VerifiablePresentation();
            tmpVerifiablePresentation.fromJson(vp.toJson());

            VpProof tmpProof = new VpProof();
            tmpProof.setType(vp.getProof().getType());
            tmpProof.setCreated(vp.getProof().getCreated());
            tmpProof.setVerificationMethod(vp.getProof().getVerificationMethod());
            tmpProof.setProofPurpose(vp.getProof().getProofPurpose());

            tmpVerifiablePresentation.setProof(tmpProof);

            System.out.println("---- ORIGIN DATA BY JAVA ----");
            System.out.println(tmpVerifiablePresentation.toJson());
            System.out.println("-----------------------------");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
