package org.omnione.did.verifier.v1.protocol.handler;

import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateRequest;
import org.omnione.did.verifier.v1.protocol.api.dto.InitiateResponse;

public interface ProtocolHandler {

    ProtocolType getProtocolType();

    InitiateResponse initiate(InitiateRequest request);
}
