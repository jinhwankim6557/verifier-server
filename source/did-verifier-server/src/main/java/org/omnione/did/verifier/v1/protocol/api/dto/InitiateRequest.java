package org.omnione.did.verifier.v1.protocol.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class InitiateRequest {

    @Schema(example = "policy-oid4vp-demo", description = "Policy ID")
    @NotBlank(message = "policyId cannot be blank")
    private String policyId;
}
