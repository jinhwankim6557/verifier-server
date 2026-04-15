package org.omnione.did.verifier.v1.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.constants.UrlConstant;
import org.omnione.did.verifier.v1.admin.api.dto.ListCredentialSchemaDto;
import org.omnione.did.verifier.v1.admin.dto.DcqlScopeMappingDTO;
import org.omnione.did.verifier.v1.admin.service.DcqlScopeMappingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = UrlConstant.Verifier.ADMIN)
@Tag(name = "DCQL Scope Mapping", description = "DCQL Scope Mapping management")
public class DcqlScopeMappingController {

    private final DcqlScopeMappingService dcqlScopeMappingService;

    @Operation(summary = "Get Scope Mapping List", description = "Get a paginated list of DCQL scope mappings.")
    @GetMapping(UrlConstant.Verifier.GET_SCOPE_MAPPING_LIST)
    public Page<DcqlScopeMappingDTO> getScopeMappingList(Pageable pageable) {
        return dcqlScopeMappingService.searchScopeMappings(pageable);
    }

    @Operation(summary = "Get Scope Mapping Info", description = "Get a single scope mapping by ID.")
    @GetMapping(UrlConstant.Verifier.GET_SCOPE_MAPPING_INFO)
    public DcqlScopeMappingDTO getScopeMappingInfo(@PathVariable Long id) {
        return dcqlScopeMappingService.getScopeMapping(id);
    }

    @Operation(summary = "Save Scope Mapping", description = "Create a new DCQL scope mapping.")
    @PostMapping(UrlConstant.Verifier.SAVE_SCOPE_MAPPING)
    public ResponseEntity<Void> saveScopeMapping(@RequestBody DcqlScopeMappingDTO dto) {
        dcqlScopeMappingService.saveScopeMapping(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update Scope Mapping", description = "Update an existing DCQL scope mapping.")
    @PutMapping(UrlConstant.Verifier.UPDATE_SCOPE_MAPPING)
    public ResponseEntity<DcqlScopeMappingDTO> updateScopeMapping(
            @PathVariable Long id, @RequestBody DcqlScopeMappingDTO dto) {
        DcqlScopeMappingDTO updated = dcqlScopeMappingService.updateScopeMapping(id, dto);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete Scope Mapping", description = "Delete a DCQL scope mapping.")
    @DeleteMapping(UrlConstant.Verifier.DELETE_SCOPE_MAPPING)
    public ResponseEntity<Void> deleteScopeMapping(@PathVariable Long id) {
        dcqlScopeMappingService.deleteScopeMapping(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search Scope Mappings for Popup", description = "Search enabled scope mappings for popup selection.")
    @GetMapping(UrlConstant.Verifier.GET_POPUP_SCOPE_MAPPING_LIST)
    public List<DcqlScopeMappingDTO> getPopupScopeMappingList(@PathVariable String searchValue) {
        return dcqlScopeMappingService.searchPopupList(searchValue);
    }

    @Operation(summary = "Get TAS Credential Schemas", description = "Fetch credential schemas from TAS for opendid_vc format.")
    @GetMapping(UrlConstant.Verifier.GET_TAS_CREDENTIAL_SCHEMAS)
    public ResponseEntity<List<ListCredentialSchemaDto>> getTasCredentialSchemas() {
        List<ListCredentialSchemaDto> schemas = dcqlScopeMappingService.getTasCredentialSchemas();
        return ResponseEntity.ok(schemas);
    }
}
