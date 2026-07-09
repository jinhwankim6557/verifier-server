package org.omnione.did.verifier.v1.protocol.service.status;

public record StatusListTokenPayload(int bits, String lst, long ttl, long exp) {}
