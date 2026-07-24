package org.omnione.did.verifier.v1.protocol.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusQueryServiceTest {

    @Mock
    private TransactionService transactionService;
    @Mock
    private VpSubmitRepository vpSubmitRepository;
    @Mock
    private Oid4vpClaimExtractionService claimExtractionService;

    @InjectMocks
    private StatusQueryService statusQueryService;

    @Test
    void completedOid4vpTransaction_attachesFormatAndClaims() {
        Transaction transaction = Transaction.builder()
                .id(1L)
                .txId("session-1")
                .type(TransactionType.OID4VP)
                .status(TransactionStatus.COMPLETED)
                .expired_at(Instant.now().plusSeconds(60))
                .build();
        VpSubmit vpSubmit = VpSubmit.builder()
                .transactionId(1L)
                .vp("{\"nationalId\":[\"<sd-jwt>\"]}")
                .format("dc+sd-jwt-did")
                .build();
        List<ClaimView> extracted = List.of(new ClaimView("given_name", "길동"));

        when(transactionService.findTransactionByTxId("session-1")).thenReturn(transaction);
        when(vpSubmitRepository.findByTransactionId(1L)).thenReturn(vpSubmit);
        when(claimExtractionService.extractClaims("{\"nationalId\":[\"<sd-jwt>\"]}")).thenReturn(extracted);

        StatusResponse response = statusQueryService.getStatus("session-1");

        assertThat(response.getProtocol()).isEqualTo(ProtocolType.OID4VP);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getFormat()).isEqualTo("dc+sd-jwt-did");
        assertThat(response.getClaims()).isEqualTo(extracted);
    }

    @Test
    void pendingOid4vpTransaction_doesNotLookUpClaims() {
        Transaction transaction = Transaction.builder()
                .id(1L)
                .txId("session-2")
                .type(TransactionType.OID4VP)
                .status(TransactionStatus.PENDING)
                .expired_at(Instant.now().plusSeconds(60))
                .build();

        when(transactionService.findTransactionByTxId("session-2")).thenReturn(transaction);

        StatusResponse response = statusQueryService.getStatus("session-2");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getFormat()).isNull();
        assertThat(response.getClaims()).isNull();
        verifyNoInteractions(vpSubmitRepository, claimExtractionService);
    }

    @Test
    void completedDidVpTransaction_doesNotLookUpClaims() {
        Transaction transaction = Transaction.builder()
                .id(1L)
                .txId("session-3")
                .type(TransactionType.VP_SUBMIT)
                .status(TransactionStatus.COMPLETED)
                .expired_at(Instant.now().plusSeconds(60))
                .build();

        when(transactionService.findTransactionByTxId("session-3")).thenReturn(transaction);

        StatusResponse response = statusQueryService.getStatus("session-3");

        assertThat(response.getProtocol()).isEqualTo(ProtocolType.DID_VP);
        assertThat(response.getClaims()).isNull();
        verifyNoInteractions(vpSubmitRepository, claimExtractionService);
    }
}
