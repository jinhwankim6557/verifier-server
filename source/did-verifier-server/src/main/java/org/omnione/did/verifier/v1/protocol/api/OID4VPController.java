package org.omnione.did.verifier.v1.protocol.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.constants.UrlConstant;
import org.omnione.did.oid4vc.oid4vp.dto.ServiceResult;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.Oid4vpResponseResult;
import org.omnione.did.verifier.v1.protocol.service.OID4VPService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = UrlConstant.Oid4vp.BASE)
@Tag(name = "OID4VP", description = "OID4VP protocol endpoints")
public class OID4VPController {

    private final OID4VPService oid4vpService;

    @Operation(summary = "Get Authorization Request",
            description = "Wallet calls this endpoint to retrieve the Authorization Request JWT via request_uri")
    @GetMapping(UrlConstant.Oid4vp.REQUEST)
    public ResponseEntity<String> getAuthorizationRequest(@PathVariable String requestId) {
        ServiceResult<String> result = oid4vpService.getAuthorizationRequest(requestId);

        String contentType = result.getContentType() != null
                ? result.getContentType()
                : MediaType.APPLICATION_JSON_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(result.getData());
    }

    @Operation(summary = "Receive VP Token Response",
            description = "Wallet submits VP Token via direct_post response mode")
    @PostMapping(UrlConstant.Oid4vp.RESPONSE)
    public ResponseEntity<Oid4vpResponseResult> receiveResponse(Oid4vpResponseRequest request) {
        Oid4vpResponseResult responseResult = oid4vpService.receiveResponse(request);
        return ResponseEntity.ok(responseResult);
    }
}
