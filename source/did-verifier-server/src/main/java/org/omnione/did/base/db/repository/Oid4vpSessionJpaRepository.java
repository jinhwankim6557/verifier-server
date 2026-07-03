package org.omnione.did.base.db.repository;

import org.omnione.did.base.db.domain.Oid4vpSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Oid4vpSessionJpaRepository extends JpaRepository<Oid4vpSession, Long> {
    Optional<Oid4vpSession> findByState(String state);
    Optional<Oid4vpSession> findByRequestId(String requestId);
    Optional<Oid4vpSession> findByTransactionId(String transactionId);
    Optional<Oid4vpSession> findByEncKid(String encKid);
}
