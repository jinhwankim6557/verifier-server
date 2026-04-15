package org.omnione.did.base.db.repository;

import org.omnione.did.base.db.domain.Oid4vpSessionMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Oid4vpSessionMappingRepository extends JpaRepository<Oid4vpSessionMapping, Long> {

    Optional<Oid4vpSessionMapping> findByTxId(String txId);

    Optional<Oid4vpSessionMapping> findByState(String state);

    Optional<Oid4vpSessionMapping> findByOid4vpRequestId(String oid4vpRequestId);
}
