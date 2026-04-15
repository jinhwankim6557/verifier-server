package org.omnione.did.verifier.v1.agent.adapter;

import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.verifier.v1.agent.service.TransactionService;
import org.omnione.did.verifier.v1.provider.TransactionProvider;
import org.omnione.did.verifier.v1.exception.VerifierSdkException;
import org.omnione.did.verifier.v1.exception.VerifierSdkErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SDK TransactionProvider 구현체
 *
 * 구현 방식:
 * - Transaction ID 생성 및 조회만 지원
 * - Transaction 상태 관리는 Application DB에서 직접 수행
 */
@Slf4j
@Component
public class TransactionProviderImpl implements TransactionProvider {

    private final TransactionService transactionService;

    public TransactionProviderImpl(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
    
    /**
     * 새로운 Transaction ID 생성 (UUID)
     * 
     * @return Transaction ID
     */
    @Override
    public String createTransactionId() {
        String txId = UUID.randomUUID().toString();
        log.debug("Generated new transaction ID: {}", txId);
        return txId;
    }
    
    /**
     * Transaction ID (Long) 조회
     *
     * txId (UUID 문자열)로 DB의 Transaction PK (Long)를 조회
     * E2E 세션 등 다른 테이블과의 연결에 필요
     *
     * @param txId Transaction ID (UUID 문자열)
     * @return Transaction PK (Long)
     * @throws VerifierSdkException 조회 실패 시
     */
    @Override
    public Long getTransactionId(String txId) {
        if (txId == null || txId.trim().isEmpty()) {
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_FIELD_REQUIRED,
                    "Transaction ID cannot be null or empty");
        }

        try {
            Transaction transaction = transactionService.findTransactionByTxId(txId);
            if (transaction == null) {
                throw new VerifierSdkException(
                        VerifierSdkErrorCode.SDK_TRANSACTION_NOT_FOUND,
                        "Transaction not found: " + txId);
            }

            Long transactionId = transaction.getId();
            log.debug("Found transaction ID {} for txId: {}", transactionId, txId);
            return transactionId;

        } catch (VerifierSdkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get transaction ID for txId: {}", txId, e);
            throw new VerifierSdkException(
                    VerifierSdkErrorCode.SDK_TRANSACTION_NOT_FOUND,
                    "Failed to get transaction ID: " + e.getMessage());
        }
    }
}
