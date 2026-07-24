package org.omnione.did.verifier.v1.protocol.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.omnione.did.base.db.constant.ProtocolType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusResponse {

    private String sessionId;

    private ProtocolType protocol;

    private String status;

    private String error;

    private String errorDescription;

    private String format;

    private List<ClaimView> claims;
}
