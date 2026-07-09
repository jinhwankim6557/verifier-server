/*
 * Copyright 2025 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.verifier.v1.agent.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.constant.TransactionStatus;
import org.omnione.did.base.db.domain.SubTransaction;
import org.omnione.did.base.db.domain.Transaction;
import org.omnione.did.base.db.domain.VpOffer;
import org.omnione.did.base.db.repository.SubTransactionRepository;
import org.omnione.did.base.db.repository.TransactionRepository;
import org.omnione.did.base.db.repository.VpOfferRepository;
import org.omnione.did.base.exception.ErrorCode;
import org.omnione.did.base.exception.OpenDidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Handles transaction and sub-transaction management, including creation,
 * retrieval, status updates, and expiration handling within the DID system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final SubTransactionRepository subTransactionRepository;
    private final VpOfferRepository vpOfferRepository;

    /**
     * Inserts a new transaction into the repository.
     *
     * @param transaction Transaction to be saved.
     * @return Saved transaction.
     */
    @Override
    public Transaction insertTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    /**
     * Finds a transaction by its transaction ID.
     *
     * @param txId Transaction ID to search for.
     * @return Found transaction.
     * @throws OpenDidException if the transaction is not found.
     */
    @Override
    public Transaction findTransactionByTxId(String txId) {
        return transactionRepository.findByTxId(txId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    /**
     * Finds a transaction by its associated offer ID.
     *
     * @param offerId Offer ID to search for.
     * @return Found transaction.
     * @throws OpenDidException if the VP offer or transaction is not found.
     */
    @Override
    public Transaction findTransactionByOfferId(String offerId) {
        VpOffer vpOffer = vpOfferRepository.findByOfferId(offerId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.VP_OFFER_NOT_FOUND));
        Long transactionId = vpOffer.getTransactionId();
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new OpenDidException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    /**
     * Saves a sub-transaction in the repository.
     *
     * @param subTransaction Sub-transaction to be saved.
     */
    @Override
    public void saveSubTransaction(SubTransaction subTransaction) {
        subTransactionRepository.save(subTransaction);
    }

    /**
     * Finds the most recent sub-transaction for a given transaction.
     *
     * @param transactionId ID of the transaction.
     * @return Most recent sub-transaction.
     * @throws OpenDidException if sub-transaction is not found.
     */
    @Override
    public SubTransaction findLastSubTransaction(Long transactionId) {
        Optional<SubTransaction> optionalSubTransaction = subTransactionRepository.findFirstByTransactionIdOrderByStepDesc(transactionId);
        if (optionalSubTransaction.isEmpty()) {
            throw new OpenDidException(ErrorCode.SUB_TRANSACTION_NOT_FOUND);
        }
        return optionalSubTransaction.get();
    }

    /**
     * Retrieves the expiration time for transactions.
     *
     * @return Expiration time (1 day from the current time).
     */
    @Override
    public Instant retrieveTransactionExpiredTime() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    /**
     * Updates the status of a transaction.
     *
     * @param id ID of the transaction.
     * @param transactionStatus New status to set for the transaction.
     * @throws OpenDidException if the transaction is not found.
     */
    @Override
    // 호출부(OID4VPService.receiveResponse 등)가 실패 시 예외를 재던지는 @Transactional 메서드라,
    // 같은 트랜잭션에 묶이면 상태 갱신(FAILED 등)까지 함께 롤백돼버린다. REQUIRES_NEW로 독립 커밋시킨다
    // — 바로 아래 updateErrorTransactionStatus와 동일한 패턴.
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTransactionStatus(Long id, TransactionStatus transactionStatus) {
        Optional<Transaction> optionalTransaction = transactionRepository.findById(id);
        if (optionalTransaction.isEmpty()) {
            throw new OpenDidException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        Transaction transaction = optionalTransaction.get();
        transaction.setStatus(transactionStatus);

        transactionRepository.save(transaction);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateErrorTransactionStatus(String txId, TransactionStatus transactionStatus) {
        Optional<Transaction> optionalTransaction = transactionRepository.findByTxId(txId);
        if (optionalTransaction.isEmpty()) {
            throw new OpenDidException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        Transaction transaction = optionalTransaction.get();
        transaction.setStatus(transactionStatus);

        transactionRepository.save(transaction);

    }
}
