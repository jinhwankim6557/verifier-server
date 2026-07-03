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

    /** direct_post.jwt: JWE Compact 문자열. 있으면 평문 필드 대신 이 값을 복호화해서 처리한다. */
    private String response;
}
