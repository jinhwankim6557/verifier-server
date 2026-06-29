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
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<Oid4vpResponseResult> receiveResponse(
            @RequestParam(value = "vp_token", required = false) String vpToken,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "presentation_submission", required = false) String presentationSubmission,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {
        // direct_post 응답은 application/x-www-form-urlencoded 로 전송된다.
        // 폼 바인딩은 @JsonProperty(snake_case)를 무시하므로 @RequestParam 으로 명시 매핑 후 DTO를 구성한다.
        Oid4vpResponseRequest request = new Oid4vpResponseRequest(
                vpToken, state, presentationSubmission, error, errorDescription);
        Oid4vpResponseResult responseResult = oid4vpService.receiveResponse(request);
        // 검증 실패(VP 무효, vp_token 파싱 실패)는 클라이언트 오류 → 400.
        // 서버 내부 오류(예상치 못한 예외)는 GlobalControllerAdvice/Spring이 500으로 처리한다.
        if ("FAILED".equals(responseResult.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseResult);
        }
        return ResponseEntity.ok(responseResult);
    }
}
