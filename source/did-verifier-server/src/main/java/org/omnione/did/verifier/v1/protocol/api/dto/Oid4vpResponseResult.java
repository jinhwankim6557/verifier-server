package org.omnione.did.verifier.v1.protocol.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Oid4vpResponseResult {

    private String sessionId;

    private String status;

    private String error;

    private String errorDescription;
}
