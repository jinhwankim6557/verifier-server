package org.omnione.did.oid4vc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.omnione.did.opendidvc.datamodel.VerifiableCredential;
import org.omnione.did.opendidvc.datamodel.VerifiablePresentation;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * SDK의 createCanonicalMessage 로직을 재현하여 mock-wallet의 signData와 비교하는 디버그 테스트.
 *
 * 사용법:
 *   1. VP_TOKEN_JSON에 mock-wallet이 전송한 VP Token JSON을 붙여넣기
 *   2. 테스트 실행: ./gradlew :did-oid4vp-sdk-server:test --tests "*.CanonicalMessageDebugTest"
 *   3. 출력된 canonical message를 mock-wallet의 [DEBUG] VC/VP signData와 비교
 */
class CanonicalMessageDebugTest {

    // ============================================================
    // *** 여기에 mock-wallet이 전송한 VP Token JSON을 붙여넣으세요 ***
    // ============================================================
    private static final String VP_TOKEN_JSON = """
            {
              "@context": ["https://www.w3.org/ns/credentials/v2"],
              "id": "test-vp-id",
              "type": ["VerifiablePresentation"],
              "holder": "did:omn:bFKK1Q6yRq6DsMoV2rjARSM3qno",
              "verifierNonce": "test-nonce",
              "verifiableCredential": [{
                "@context": ["https://www.w3.org/ns/credentials/v2"],
                "id": "test-vc-id",
                "type": ["VerifiableCredential"],
                "issuer": {"id": "did:omn:issuer"},
                "validFrom": "2026-01-01T00:00:00Z",
                "credentialSubject": {
                  "id": "did:omn:bFKK1Q6yRq6DsMoV2rjARSM3qno",
                  "claims": []
                },
                "proof": {
                  "type": "Secp256r1Signature2018",
                  "created": "2026-01-01T00:00:00Z",
                  "verificationMethod": "did:omn:issuer?versionId=1#assert",
                  "proofPurpose": "assertionMethod",
                  "proofValue": "zMOCK_PROOF_VALUE"
                }
              }],
              "proof": {
                "type": "Secp256r1Signature2018",
                "created": "2026-01-01T00:00:00Z",
                "verificationMethod": "did:omn:bFKK1Q6yRq6DsMoV2rjARSM3qno?versionId=1#auth",
                "proofPurpose": "authentication",
                "domain": "test-domain",
                "challenge": "test-nonce",
                "proofValue": "zMOCK_VP_PROOF"
              }
            }
            """;

    /** SDK 내부에서 사용하는 것과 동일한 ObjectMapper 설정 */
    private static final ObjectMapper SDK_MAPPER = initializeObjectMapper();

    private static ObjectMapper initializeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    @Test
    void debugCanonicalMessage() throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("SDK Canonical Message Debug Test");
        System.out.println("=".repeat(80));

        // 1. VP Token JSON → VerifiablePresentation (SDK와 동일하게 plain ObjectMapper로 역직렬화)
        ObjectMapper plainMapper = new ObjectMapper();
        VerifiablePresentation vp = plainMapper.readValue(VP_TOKEN_JSON, VerifiablePresentation.class);

        System.out.println("\n[1] VP deserialized successfully");
        System.out.println("    VP id: " + vp.getId());
        System.out.println("    VP holder: " + vp.getHolder());
        System.out.println("    VP proof type: " + (vp.getProof() != null ? vp.getProof().getType() : "null"));
        System.out.println("    VC count: " + (vp.getVerifiableCredential() != null ? vp.getVerifiableCredential().size() : 0));

        // 2. VP canonical message (VP 서명 검증용)
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[2] VP Canonical Message (for VP signature verification)");
        System.out.println("    SDK uses holderPublicKey to verify this");
        System.out.println("=".repeat(80));
        String vpCanonical = createCanonicalMessage(vp);
        System.out.println("\nVP Canonical String:");
        System.out.println(vpCanonical);
        System.out.println("\nVP Canonical Hex:");
        printHex(vpCanonical.getBytes(StandardCharsets.UTF_8));

        // 3. VC canonical message (VC 서명 검증용)
        if (vp.getVerifiableCredential() != null && !vp.getVerifiableCredential().isEmpty()) {
            VerifiableCredential vc = vp.getVerifiableCredential().get(0);
            System.out.println("\n" + "=".repeat(80));
            System.out.println("[3] VC Canonical Message (for VC signature verification)");
            System.out.println("    SDK uses issuerPublicKey to verify this");
            System.out.println("=".repeat(80));
            String vcCanonical = createCanonicalMessage(vc);
            System.out.println("\nVC Canonical String:");
            System.out.println(vcCanonical);
            System.out.println("\nVC Canonical Hex:");
            printHex(vcCanonical.getBytes(StandardCharsets.UTF_8));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("위 canonical 문자열을 mock-wallet의 [DEBUG] signData와 비교하세요.");
        System.out.println("바이트 단위 차이가 서명 검증 실패의 원인입니다.");
        System.out.println("=".repeat(80));
    }

    /**
     * SDK의 OpenDIDVCVerifier.createCanonicalMessage()와 동일한 로직 재현.
     *
     * 1. Object → JSON string (NON_NULL, ORDER_MAP_ENTRIES_BY_KEYS)
     * 2. JSON → typed Java object (재역직렬화)
     * 3. proof.proofValue = null
     * 4. serializeAndSort → alphabetically sorted JSON
     * 5. removeEscapeCharactersExceptValues
     */
    private String createCanonicalMessage(Object obj) throws Exception {
        // Step 1: serialize
        String json = SDK_MAPPER.writeValueAsString(obj);
        System.out.println("\n  [Step1] Serialized JSON (before re-parse):");
        System.out.println("  " + json.substring(0, Math.min(200, json.length())) + "...");

        // Step 2: deserialize back to typed object
        Object deserialized;
        if (obj instanceof VerifiablePresentation) {
            VerifiablePresentation vpObj = SDK_MAPPER.readValue(json, VerifiablePresentation.class);
            // Step 3: clear proofValue
            if (vpObj.getProof() != null) {
                vpObj.getProof().setProofValue(null);
            }
            deserialized = vpObj;
        } else if (obj instanceof VerifiableCredential) {
            VerifiableCredential vcObj = SDK_MAPPER.readValue(json, VerifiableCredential.class);
            if (vcObj.getProof() != null) {
                vcObj.getProof().setProofValue(null);
            }
            deserialized = vcObj;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass().getName());
        }

        // Step 4: serializeAndSort
        String sortedJson = serializeAndSort(deserialized);

        System.out.println("  [Step4] After serializeAndSort:");
        System.out.println("  " + sortedJson.substring(0, Math.min(200, sortedJson.length())) + "...");

        return sortedJson;
    }

    /** SDK의 serializeAndSort 재현 */
    private String serializeAndSort(Object obj) throws Exception {
        String json = SDK_MAPPER.writeValueAsString(obj);
        JsonNode tree = SDK_MAPPER.readTree(json);
        JsonNode sorted = sortJsonNode(tree);
        String result = SDK_MAPPER.writeValueAsString(sorted);
        return removeEscapeCharactersExceptValues(result);
    }

    /** SDK의 sortJsonNode 재현 - TreeMap으로 재귀적 키 정렬 */
    private JsonNode sortJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sortedNode = SDK_MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                sorted.put(entry.getKey(), sortJsonNode(entry.getValue()));
            }
            sorted.forEach(sortedNode::set);
            return sortedNode;
        }
        if (node.isArray()) {
            ArrayNode sortedArray = SDK_MAPPER.createArrayNode();
            for (JsonNode element : node) {
                sortedArray.add(sortJsonNode(element));
            }
            return sortedArray;
        }
        return node;
    }

    /** SDK의 removeEscapeCharactersExceptValues 재현 */
    private String removeEscapeCharactersExceptValues(String json) {
        StringBuilder result = new StringBuilder();
        boolean insideString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                insideString = !insideString;
            }
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (!insideString && (next == '"' || next == '/')) {
                    continue; // skip backslash
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    private void printHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if ((i + 1) % 64 == 0) sb.append("\n");
        }
        System.out.println(sb);
    }
}
