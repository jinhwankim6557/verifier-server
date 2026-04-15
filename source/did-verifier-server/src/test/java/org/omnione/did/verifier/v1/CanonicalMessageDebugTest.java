package org.omnione.did.verifier.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;
import org.omnione.did.opendidvc.datamodel.VerifiableCredential;
import org.omnione.did.opendidvc.datamodel.VerifiablePresentation;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * mock-wallet이 서명한 VP를 서버 Canonical 로직으로 재구성해 Secp256r1Verifier와 동일한 방식으로 검증한다.
 *
 * 사용 방법:
 *   1) mock-wallet에서 Case 1 실행 후 서버 로그의 vpTokenJson(VP 원문) 을 {@link #VP_TOKEN_JSON} 에 붙여넣는다.
 *   2) mock-wallet에서 사용하는 압축 holder 공개키(Base64)는 {@link #HOLDER_PUBLIC_KEY_BASE64}
 *      (33바이트, 앞자리 0x02/0x03) 에 붙여넣는다. DID Doc의 issuer #assert 키는
 *      {@link #ISSUER_PUBLIC_KEY_BASE64}.
 *   3) `./gradlew test --tests CanonicalMessageDebugTest.compareCanonicalMessage`
 */
class CanonicalMessageDebugTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String HOLDER_PUBLIC_KEY_BASE64 = "Ag4JPxfnfC49llxFuVQsLxrw70P3X79ywR2mjPexCRJg";
    private static final String ISSUER_PUBLIC_KEY_BASE64 = "Ag4JPxfnfC49llxFuVQsLxrw70P3X79ywR2mjPexCRJg";

    /** 서버 로그 "vpTokenJson : ..." 뒤의 JSON 전체를 그대로 붙여넣는다. */
    private static final String VP_TOKEN_JSON = "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],\"id\":\"f04f5298-3c98-4685-bd17-b288c38cb5c3\",\"type\":[\"VerifiablePresentation\"],\"holder\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD\",\"validFrom\":\"2026-04-14T01:53:36.000Z\",\"validUntil\":\"2026-04-15T01:53:36.000Z\",\"verifierNonce\":\"0ca7a90d-87d4-449f-a65c-d7a902dd685d\",\"verifiableCredential\":[{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],\"id\":\"2fd03f62-a1b5-48b2-86ae-b255c2bffb98\",\"type\":[\"VerifiableCredential\"],\"issuer\":{\"id\":\"did:omn:issuer\",\"name\":\"OpenDID University\"},\"validFrom\":\"2026-04-14T01:53:36.000Z\",\"validUntil\":\"2027-04-14T01:53:36.000Z\",\"credentialSubject\":{\"id\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD\",\"claims\":[{\"code\":\"Test1234.name\",\"caption\":\"Name\",\"value\":\"Gildong Hong\",\"type\":\"text\",\"format\":\"plain\",\"hideValue\":false}]},\"credentialSchema\":{\"id\":\"http://localhost:8091/issuer/api/v1/vc/vcschema?name=Test1234Schenm\",\"type\":\"OsdSchemaCredential\"},\"proof\":{\"type\":\"Secp256r1Signature2018\",\"created\":\"2026-01-01T00:00:00Z\",\"verificationMethod\":\"did:omn:issuer?versionId=1#assert\",\"proofPurpose\":\"assertionMethod\",\"proofValue\":\"z3oqhA4Ry6vG23X6HFomPven4BctzfUfzv28hCdz6GHuVhAQnDNthJ6YvSrzP1DvoJeTLVB8st1bMiuvDa9CKXAKmL\"}}],\"proof\":{\"type\":\"Secp256r1Signature2018\",\"created\":\"2026-04-14T01:53:36.000Z\",\"verificationMethod\":\"did:omn:KJbu1ZiLngnDQwZ7oMHsCGzL2tD?versionId=1#auth\",\"proofPurpose\":\"authentication\",\"domain\":\"decentralized_identity:did:omn:verifier\",\"challenge\":\"0ca7a90d-87d4-449f-a65c-d7a902dd685d\",\"proofValue\":\"z3nDYugzLvpCKYfbVtyEmkqkGiR7LrKdTrgVnXkbmw8kYw3BMfWg8pUiTYjSWpW8cmX8GrrKTTBTkHrPCS2mFFw5qn\"}}";

    private static final ObjectMapper OBJECT_MAPPER = initializeObjectMapper();

    private static ObjectMapper initializeObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return om;
    }

    @Test
    void compareCanonicalMessage() throws Exception {
        if (VP_TOKEN_JSON == null || VP_TOKEN_JSON.isBlank()) {
            System.out.println("[SKIP] VP_TOKEN_JSON 값을 채운 뒤 다시 실행하세요.");
            return;
        }

        VerifiablePresentation vp = OBJECT_MAPPER.readValue(VP_TOKEN_JSON, VerifiablePresentation.class);

        String vpCanonical = createCanonicalMessage(vp, true);
        System.out.println("===== VP Canonical Message (서버) =====");
        System.out.println(vpCanonical);
        System.out.println("VP Canonical Hex: " + bytesToHex(vpCanonical.getBytes(StandardCharsets.UTF_8)));
        System.out.println("VP Canonical SHA-256: " + bytesToHex(sha256(vpCanonical.getBytes(StandardCharsets.UTF_8))));

        String vpProofValue = vp.getProof().getProofValue();
        System.out.println("\nVP proofValue: " + vpProofValue);
        System.out.println("VP signature verify (holder): "
                + verifySignature(HOLDER_PUBLIC_KEY_BASE64, vpCanonical.getBytes(StandardCharsets.UTF_8), vpProofValue));

        if (vp.getVerifiableCredential() != null && !vp.getVerifiableCredential().isEmpty()) {
            VerifiableCredential vc = vp.getVerifiableCredential().get(0);
            String vcCanonical = createCanonicalMessage(vc, false);
            System.out.println("\n===== VC Canonical Message (서버) =====");
            System.out.println(vcCanonical);
            System.out.println("VC Canonical Hex: " + bytesToHex(vcCanonical.getBytes(StandardCharsets.UTF_8)));
            System.out.println("VC Canonical SHA-256: " + bytesToHex(sha256(vcCanonical.getBytes(StandardCharsets.UTF_8))));

            String vcProofValue = vc.getProof().getProofValue();
            System.out.println("\nVC proofValue: " + vcProofValue);
            System.out.println("VC signature verify (issuer): "
                    + verifySignature(ISSUER_PUBLIC_KEY_BASE64, vcCanonical.getBytes(StandardCharsets.UTF_8), vcProofValue));
        }

        System.out.println("\n===== 필드 손실 체크 (원본 JSON vs 재직렬화 JSON) =====");
        JsonNode originalTree = OBJECT_MAPPER.readTree(VP_TOKEN_JSON);
        JsonNode reserialized = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(vp));
        diffKeys("", originalTree, reserialized);
    }

    private static String createCanonicalMessage(Object obj, boolean isVp) throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(obj);
        Object copy;
        if (isVp) {
            copy = OBJECT_MAPPER.readValue(json, VerifiablePresentation.class);
            VerifiablePresentation vp = (VerifiablePresentation) copy;
            if (vp.getProof() != null) vp.getProof().setProofValue(null);
        } else {
            copy = OBJECT_MAPPER.readValue(json, VerifiableCredential.class);
            VerifiableCredential vc = (VerifiableCredential) copy;
            if (vc.getProof() != null) vc.getProof().setProofValue(null);
        }
        String copyJson = OBJECT_MAPPER.writeValueAsString(copy);
        JsonNode sorted = sortJsonNode(OBJECT_MAPPER.readTree(copyJson));
        String sortedJson = OBJECT_MAPPER.writeValueAsString(sorted);
        return removeEscapeCharactersExceptValues(sortedJson);
    }

    private static JsonNode sortJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = OBJECT_MAPPER.createObjectNode();
            TreeMap<String, JsonNode> tree = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                tree.put(e.getKey(), sortJsonNode(e.getValue()));
            }
            tree.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = OBJECT_MAPPER.createArrayNode();
            for (JsonNode child : node) array.add(sortJsonNode(child));
            return array;
        }
        return node;
    }

    private static String removeEscapeCharactersExceptValues(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') inString = !inString;
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (!inString && (next == '"' || next == '/')) continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean verifySignature(String base64CompressedKey, byte[] message, String proofValue) {
        try {
            byte[] compressed = Base64.getDecoder().decode(base64CompressedKey);
            PublicKey publicKey = toEcPublicKey(compressed);
            byte[] compact = decodeProofValue(proofValue);
            if (compact.length != 65) {
                System.out.println("  decoded signature length != 65: " + compact.length);
                return false;
            }
            byte[] der = toDer(compact, 1);
            Signature sig = Signature.getInstance("SHA256withECDSA", new BouncyCastleProvider());
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(der);
        } catch (Exception e) {
            System.out.println("  verify error: " + e.getMessage());
            return false;
        }
    }

    private static PublicKey toEcPublicKey(byte[] compressed33) throws Exception {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256r1");
        ECPoint point = spec.getCurve().decodePoint(compressed33);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, spec);
        return KeyFactory.getInstance("EC", new BouncyCastleProvider()).generatePublic(keySpec);
    }

    private static byte[] decodeProofValue(String value) {
        char prefix = value.charAt(0);
        String body = value.substring(1);
        switch (prefix) {
            case 'z':
            case 'Z':
                return base58Decode(body);
            case 'm':
            case 'M':
                return Base64.getDecoder().decode(padBase64(body));
            default:
                return Base64.getDecoder().decode(padBase64(value));
        }
    }

    private static String padBase64(String s) {
        int pad = (4 - (s.length() % 4)) % 4;
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < pad; i++) sb.append('=');
        return sb.toString();
    }

    private static final String B58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static byte[] base58Decode(String input) {
        java.math.BigInteger result = java.math.BigInteger.ZERO;
        java.math.BigInteger base = java.math.BigInteger.valueOf(58);
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) leadingZeros++;
        for (int i = 0; i < input.length(); i++) {
            int idx = B58_ALPHABET.indexOf(input.charAt(i));
            if (idx < 0) throw new IllegalArgumentException("Bad b58 char: " + input.charAt(i));
            result = result.multiply(base).add(java.math.BigInteger.valueOf(idx));
        }
        byte[] withoutLeading = result.toByteArray();
        int start = (withoutLeading.length > 0 && withoutLeading[0] == 0) ? 1 : 0;
        byte[] out = new byte[leadingZeros + withoutLeading.length - start];
        System.arraycopy(withoutLeading, start, out, leadingZeros, withoutLeading.length - start);
        return out;
    }

    private static byte[] toDer(byte[] compact65, int offset) {
        byte[] r = slice(compact65, offset, 32);
        byte[] s = slice(compact65, offset + 32, 32);
        byte[] rEnc = intPositive(r);
        byte[] sEnc = intPositive(s);
        int seqLen = 2 + rEnc.length + 2 + sEnc.length;
        byte[] out = new byte[2 + seqLen];
        int i = 0;
        out[i++] = 0x30;
        out[i++] = (byte) seqLen;
        out[i++] = 0x02;
        out[i++] = (byte) rEnc.length;
        System.arraycopy(rEnc, 0, out, i, rEnc.length); i += rEnc.length;
        out[i++] = 0x02;
        out[i++] = (byte) sEnc.length;
        System.arraycopy(sEnc, 0, out, i, sEnc.length);
        return out;
    }

    private static byte[] slice(byte[] src, int offset, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, offset, out, 0, len);
        return out;
    }

    private static byte[] intPositive(byte[] v) {
        int start = 0;
        while (start < v.length - 1 && v[start] == 0) start++;
        if ((v[start] & 0x80) != 0) {
            byte[] out = new byte[v.length - start + 1];
            System.arraycopy(v, start, out, 1, v.length - start);
            return out;
        }
        byte[] out = new byte[v.length - start];
        System.arraycopy(v, start, out, 0, v.length - start);
        return out;
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void diffKeys(String prefix, JsonNode original, JsonNode reserialized) {
        if (original.isObject() && reserialized.isObject()) {
            java.util.Set<String> originals = new java.util.TreeSet<>();
            java.util.Set<String> reserialiseds = new java.util.TreeSet<>();
            original.fieldNames().forEachRemaining(originals::add);
            reserialized.fieldNames().forEachRemaining(reserialiseds::add);
            for (String k : originals) {
                if (!reserialiseds.contains(k)) {
                    System.out.println("[LOST IN RESERIALIZE] " + prefix + k);
                } else {
                    diffKeys(prefix + k + ".", original.get(k), reserialized.get(k));
                }
            }
            for (String k : reserialiseds) {
                if (!originals.contains(k)) System.out.println("[ADDED IN RESERIALIZE] " + prefix + k);
            }
        } else if (original.isArray() && reserialized.isArray()) {
            int n = Math.min(original.size(), reserialized.size());
            for (int i = 0; i < n; i++) diffKeys(prefix + "[" + i + "].", original.get(i), reserialized.get(i));
        }
    }
}
