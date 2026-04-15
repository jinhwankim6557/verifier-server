package org.omnione.did.verifier.v1.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.constants.UrlConstant;
import org.omnione.did.verifier.v1.admin.service.Oid4vpConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = UrlConstant.Verifier.ADMIN)
@Tag(name = "OID4VP Config", description = "OID4VP configuration management")
public class Oid4vpConfigController {

    private final Oid4vpConfigService oid4vpConfigService;

    @Operation(summary = "Get OID4VP Config", description = "Get the current OID4VP configuration.")
    @GetMapping(UrlConstant.Verifier.GET_OID4VP_CONFIG)
    public Map<String, Object> getConfig() {
        return oid4vpConfigService.getConfig();
    }

    @Operation(summary = "Update OID4VP Config", description = "Update OID4VP configuration and reload SDK cache.")
    @PutMapping(UrlConstant.Verifier.UPDATE_OID4VP_CONFIG)
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, Object> configMap) {
        oid4vpConfigService.saveConfig(configMap);
        return ResponseEntity.noContent().build();
    }
}
