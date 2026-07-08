package org.omnione.did.verifier.v1.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.Oid4vpConfig;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.omnione.did.oid4vc.oid4vp.config.OID4VPConfig;
import org.omnione.did.oid4vc.oid4vp.service.VerifierConfigService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class Oid4vpConfigService {

    private static final String CONFIG_TYPE = "OID4VP";

    private final Oid4vpConfigRepository oid4vpConfigRepository;
    private final VerifierConfigService verifierConfigService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getConfig() {
        Oid4vpConfig entity = oid4vpConfigRepository.findByType(CONFIG_TYPE)
                .orElse(null);

        if (entity == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(entity.getConfig(), Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse OID4VP config JSON", e);
            throw new OpenDidException(ErrorCode.FAILED_TO_INITIATE_VERIFICATION);
        }
    }

    public void saveConfig(Map<String, Object> configMap) {
        try {
            String configJson = objectMapper.writeValueAsString(configMap);

            // validate by parsing as OID4VPConfig
            OID4VPConfig parsedConfig = objectMapper.readValue(configJson, OID4VPConfig.class);
            validateEncryptionAlgorithm(parsedConfig);

            Oid4vpConfig entity = oid4vpConfigRepository.findByType(CONFIG_TYPE)
                    .orElse(Oid4vpConfig.builder().type(CONFIG_TYPE).build());
            entity.setConfig(configJson);
            oid4vpConfigRepository.save(entity);

            // reload SDK cache
            verifierConfigService.reloadConfig();
            log.info("OID4VP config saved and SDK cache reloaded");
        } catch (JsonProcessingException e) {
            log.error("Invalid OID4VP config JSON", e);
            throw new OpenDidException(ErrorCode.FAILED_TO_INITIATE_VERIFICATION, e);
        }
    }

    /**
     * SDK의 JweResponseDecryptor는 ECDH-ES/A256GCM을 하드코딩해서 강제하며 이 config를 읽지 않는다
     * (의도적 보안 결정 — 알고리즘 협상 없음, 앱 개발팀과 ECDH-ES/A256GCM 고정 합의 완료).
     * 이 config의 encryption.alg/enc는 그 고정값을 지갑에 advertise하는 용도일 뿐이라, 다른 값을 저장하면
     * 지갑에는 다른 값을 advertise하면서 실제 복호화는 항상 실패하는 조용한 불일치가 생긴다.
     * 저장 시점에 명시적으로 거부해 그 드리프트를 막는다(최종 리뷰 #5 반영).
     */
    private void validateEncryptionAlgorithm(OID4VPConfig config) {
        OID4VPConfig.Encryption encryption = config.getEncryption();
        String alg = encryption != null ? encryption.getAlg() : null;
        String enc = encryption != null ? encryption.getEnc() : null;

        if (!JWEAlgorithm.ECDH_ES.getName().equals(alg) || !EncryptionMethod.A256GCM.getName().equals(enc)) {
            log.warn("Rejected OID4VP config save: unsupported encryption alg/enc (alg={}, enc={})", alg, enc);
            throw new OpenDidException(ErrorCode.OID4VP_ENCRYPTION_ALGORITHM_NOT_SUPPORTED);
        }
    }
}
