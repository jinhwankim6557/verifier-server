package org.omnione.did.verifier.v1.protocol.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClaimView {
    private final String caption;
    private final String value;
}
