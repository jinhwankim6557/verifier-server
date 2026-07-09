// StatusListTokenVerifier.java
package org.omnione.did.verifier.v1.protocol.service.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.base.util.BaseCoreDidUtil;
import org.omnione.did.data.model.did.DidDocument;
import org.omnione.did.data.model.did.VerificationMethod;
import org.omnione.did.oid4vc.oid4vp.util.crypto.MultibaseUtils;
import org.omnione.did.verifier.v1.agent.service.DidDocService;
import org.springframework.stereotype.Component;

import java.security.interfaces.ECPublicKey;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusListTokenVerifier {

    private final DidDocService didDocService;
    private final ObjectMapper objectMapper;

    public StatusListTokenPayload verify(String jwt, String expectedUri) {
        try {
            JWSObject jwsObject = JWSObject.parse(jwt);

            // 1. kid로 공개키 해석
            String kid = jwsObject.getHeader().getKeyID();
            if (kid == null) throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
            ECPublicKey publicKey = resolvePublicKey(kid);

            // 2. 서명 검증
            JWSVerifier verifier = new ECDSAVerifier(publicKey);
            if (!jwsObject.verify(verifier)) {
                log.warn("Status list token signature verification failed for uri: {}", expectedUri);
                throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
            }

            // 3. payload 파싱
            byte[] payloadBytes = jwsObject.getPayload().toBytes();
            JsonNode payload = objectMapper.readTree(payloadBytes);

            // 4. sub == expectedUri
            String sub = payload.path("sub").asText(null);
            if (!expectedUri.equals(sub)) {
                log.warn("Status list token sub mismatch: expected={}, actual={}", expectedUri, sub);
                throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
            }

            // 5. exp 만료 확인
            long exp = payload.path("exp").asLong(0);
            if (exp > 0 && Instant.now().getEpochSecond() > exp) {
                log.warn("Status list token expired for uri: {}", expectedUri);
                throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
            }

            // 6. status_list 클레임 추출
            JsonNode statusList = payload.path("status_list");
            int bits = statusList.path("bits").asInt(1);
            String lst = statusList.path("lst").asText(null);
            long ttl = payload.path("ttl").asLong(0);

            if (lst == null) throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);

            return new StatusListTokenPayload(bits, lst, ttl, exp);

        } catch (OpenDidException e) {
            throw e;
        } catch (Exception e) {
            log.error("Status list token verification failed: {}", e.getMessage());
            throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }

    private ECPublicKey resolvePublicKey(String kid) {
        // "did:omn:issuer?versionId=1#auth" 형태를 고려해 '#' 이전에 '?'가 있으면 그 지점을 DID 경계로 삼는다.
        // (OID4VPService.extractDid/extractKeyId와 동일한 파싱 규칙)
        int queryIdx = kid.indexOf('?');
        int hashIdx = kid.indexOf('#');
        String did;
        if (queryIdx > 0) {
            did = kid.substring(0, queryIdx);
        } else if (hashIdx > 0) {
            did = kid.substring(0, hashIdx);
        } else {
            did = kid;
        }
        String keyId = hashIdx >= 0 ? kid.substring(hashIdx + 1) : "";

        DidDocument didDoc = didDocService.getDidDocument(did);
        VerificationMethod vm = BaseCoreDidUtil.getVerificationMethod(didDoc, keyId);

        try {
            byte[] keyBytes = MultibaseUtils.decodeMultibase(vm.getPublicKeyMultibase());
            return decodeP256PublicKey(keyBytes);
        } catch (Exception e) {
            throw new OpenDidException(ErrorCode.STATUS_LIST_TOKEN_INVALID);
        }
    }

    private ECPublicKey decodeP256PublicKey(byte[] compressed) throws Exception {
        // compressed 33-byte P-256 → ECPublicKey
        org.bouncycastle.jce.spec.ECNamedCurveParameterSpec spec =
                org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("P-256");
        org.bouncycastle.math.ec.ECPoint point = spec.getCurve().decodePoint(compressed);
        org.bouncycastle.jce.spec.ECPublicKeySpec pubKeySpec =
                new org.bouncycastle.jce.spec.ECPublicKeySpec(point, spec);
        return (ECPublicKey) java.security.KeyFactory.getInstance("EC", "BC")
                .generatePublic(pubKeySpec);
    }
}
