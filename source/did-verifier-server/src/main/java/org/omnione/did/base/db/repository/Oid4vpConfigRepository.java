package org.omnione.did.base.db.repository;

import org.omnione.did.base.db.domain.Oid4vpConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Oid4vpConfigRepository extends JpaRepository<Oid4vpConfig, Long> {

    Optional<Oid4vpConfig> findByType(String type);
}
