package org.omnione.did.verifier.v1.protocol.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Oid4vpResponseRequest {

    @JsonProperty("vp_token")
    private String vpToken;

    private String state;

    @JsonProperty("presentation_submission")
    private String presentationSubmission;

    private String error;

    @JsonProperty("error_description")
    private String errorDescription;
}
