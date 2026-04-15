package org.omnione.did.verifier.v1.protocol.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.constants.UrlConstant;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;
import org.omnione.did.verifier.v1.protocol.api.dto.StatusResponse;
import org.omnione.did.verifier.v1.protocol.orchestrator.StatusQueryService;
import org.omnione.did.verifier.v1.protocol.orchestrator.VerificationOrchestrator;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(value = UrlConstant.Verifier.V2)
@Tag(name = "Unified Verification", description = "Protocol-agnostic verification API")
public class UnifiedVerificationController {

    private final VerificationOrchestrator verificationOrchestrator;
    private final StatusQueryService statusQueryService;

    @Operation(summary = "Initiate Verification",
            description = "Initiates verification based on policy's protocol type (DID VP or OID4VP)")
    @PostMapping(UrlConstant.Verifier.INITIATE)
    public InitiateResponse initiate(@RequestBody @Valid InitiateRequest request) {
        return verificationOrchestrator.initiate(request);
    }

    @Operation(summary = "Get Verification Status",
            description = "Returns the current status of a verification session")
    @GetMapping(UrlConstant.Verifier.STATUS)
    public StatusResponse getStatus(@PathVariable String sessionId) {
        return statusQueryService.getStatus(sessionId);
    }
}
