package org.omnione.did.verifier.v1.protocol.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.omnione.did.base.db.constant.ProtocolType;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitiateResponse {

    private ProtocolType protocol;

    private String sessionId;

    /** DID VP: VpOfferPayload JSON */
    private Object payload;

    /** OID4VP: authorization request URI or object */
    private String authorizationRequest;

    /** Protocol-specific next endpoints */
    private Map<String, String> nextEndpoints;
}
