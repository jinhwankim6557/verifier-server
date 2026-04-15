package org.omnione.did.verifier.v1.protocol.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.protocol.api.dto.StatusResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusQueryService {

    private final TransactionService transactionService;

    public StatusResponse getStatus(String sessionId) {
        log.debug("=== StatusQueryService.getStatus sessionId={} ===", sessionId);

        Transaction transaction = transactionService.findTransactionByTxId(sessionId);

        // 만료 체크: PENDING 상태인데 expired_at이 지났으면 EXPIRED로 간주
        String status = transaction.getStatus().name();
        if (transaction.getStatus() == TransactionStatus.PENDING
                && transaction.getExpired_at().isBefore(Instant.now())) {
            status = "EXPIRED";
        }

        ProtocolType protocol = transaction.getType() == TransactionType.OID4VP
                ? ProtocolType.OID4VP
                : ProtocolType.DID_VP;

        return StatusResponse.builder()
                .sessionId(sessionId)
                .protocol(protocol)
                .status(status)
                .build();
    }
}
