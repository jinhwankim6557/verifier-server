package org.omnione.did.verifier.v1.protocol.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.ProtocolType;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.constant.TransactionType;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.domain.VpSubmit;
import org.omnione.did.base.db.repository.VpSubmitRepository;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.protocol.api.dto.ClaimView;
import org.omnione.did.verifier.v1.protocol.api.dto.StatusResponse;
import org.omnione.did.verifier.v1.protocol.service.Oid4vpClaimExtractionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusQueryService {

    private final TransactionService transactionService;
    private final VpSubmitRepository vpSubmitRepository;
    private final Oid4vpClaimExtractionService claimExtractionService;

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

        // OID4VP 제출이 완료된 경우에만 confirm 화면 표시용 claim을 재파싱해 붙인다.
        String format = null;
        List<ClaimView> claims = null;
        if (protocol == ProtocolType.OID4VP && "COMPLETED".equals(status)) {
            VpSubmit vpSubmit = vpSubmitRepository.findByTransactionId(transaction.getId());
            if (vpSubmit != null && vpSubmit.getVp() != null) {
                format = vpSubmit.getFormat();
                claims = claimExtractionService.extractClaims(vpSubmit.getVp());
            }
        }

        return StatusResponse.builder()
                .sessionId(sessionId)
                .protocol(protocol)
                .status(status)
                .format(format)
                .claims(claims)
                .build();
    }
}
