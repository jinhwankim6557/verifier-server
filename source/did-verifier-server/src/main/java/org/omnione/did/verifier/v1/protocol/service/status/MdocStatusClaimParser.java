package org.omnione.did.verifier.v1.protocol.service.status;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.mdoc.core.oid4vp.MDocParser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * mso_mdoc의 MSO에 담긴 status list 참조를 추출한다.
 *
 * <p>구조는 SD-JWT의 status claim과 동일하되 CBOR로 인코딩되어 IssuerAuth(COSE_Sign1)의
 * payload(MSO) 안에 들어 있다.
 *
 * <pre>
 * MSO = { ..., "status": { "status_list": { "idx": 6, "uri": "https://.../status-lists/2" } } }
 * </pre>
 *
 * <p>여기서는 참조만 꺼낸다. MSO 자체의 진위는 IssuerAuth 서명 검증(SDK)이 이미 보장하므로,
 * 폐기 상태 조회·검증은 SD-JWT와 완전히 동일한 파이프라인({@link CredentialStatusChecker})을 탄다.
 */
@Slf4j
@Component
public class MdocStatusClaimParser {

    private static final String ISSUER_SIGNED = "issuerSigned";
    private static final String ISSUER_AUTH = "issuerAuth";
    private static final String STATUS = "status";
    private static final String STATUS_LIST = "status_list";
    private static final int COSE_SIGN1_PAYLOAD_INDEX = 2;
    private static final int MAX_BYTE_STRING_UNWRAP = 3;

    public Optional<StatusListRef> parse(String mdocBase64) {
        try {
            CBORObject mso = extractMso(mdocBase64);
            if (mso == null) return Optional.empty();

            CBORObject status = mso.get(STATUS);
            if (status == null || status.getType() != CBORType.Map) return Optional.empty();

            CBORObject statusList = status.get(STATUS_LIST);
            if (statusList == null || statusList.getType() != CBORType.Map) return Optional.empty();

            CBORObject idx = statusList.get("idx");
            CBORObject uri = statusList.get("uri");
            if (idx == null || uri == null) return Optional.empty();

            return Optional.of(new StatusListRef(idx.AsInt32Value(), uri.AsString()));
        } catch (Exception e) {
            log.debug("Failed to parse status claim from mso_mdoc: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** IssuerAuth(COSE_Sign1)의 payload를 풀어 MSO 맵을 반환한다. */
    private CBORObject extractMso(String mdocBase64) throws Exception {
        CBORObject document = MDocParser.extractFirstDocument(mdocBase64);

        CBORObject issuerSigned = document.get(ISSUER_SIGNED);
        if (issuerSigned == null) return null;

        CBORObject issuerAuth = issuerSigned.get(ISSUER_AUTH);
        if (issuerAuth == null) return null;
        if (issuerAuth.isTagged()) {
            // COSE_Sign1은 tag 18로 감싸져 올 수 있다.
            issuerAuth = issuerAuth.Untag();
        }
        if (issuerAuth.getType() != CBORType.Array || issuerAuth.size() <= COSE_SIGN1_PAYLOAD_INDEX) {
            return null;
        }

        // payload는 tag 24(bstr로 감싼 CBOR) 형태로 들어온다.
        CBORObject mso = issuerAuth.get(COSE_SIGN1_PAYLOAD_INDEX);
        for (int i = 0; i < MAX_BYTE_STRING_UNWRAP && mso != null
                && mso.getType() == CBORType.ByteString; i++) {
            mso = CBORObject.DecodeFromBytes(mso.GetByteString());
        }
        if (mso != null && mso.isTagged()) {
            mso = mso.Untag();
        }
        return mso != null && mso.getType() == CBORType.Map ? mso : null;
    }
}
