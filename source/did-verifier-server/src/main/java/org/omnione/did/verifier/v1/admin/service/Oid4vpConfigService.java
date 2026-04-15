package org.omnione.did.verifier.v1.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.Oid4vpConfig;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
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
            objectMapper.readValue(configJson, org.omnione.did.oid4vc.oid4vp.config.OID4VPConfig.class);

            Oid4vpConfig entity = oid4vpConfigRepository.findByType(CONFIG_TYPE)
                    .orElse(Oid4vpConfig.builder().type(CONFIG_TYPE).build());
            entity.setConfig(configJson);
            oid4vpConfigRepository.save(entity);

            // reload SDK cache
            verifierConfigService.reloadConfig();
            log.info("OID4VP config saved and SDK cache reloaded");
        } catch (JsonProcessingException e) {
            log.error("Invalid OID4VP config JSON", e);
            throw new OpenDidException(ErrorCode.FAILED_TO_INITIATE_VERIFICATION);
        }
    }
}
