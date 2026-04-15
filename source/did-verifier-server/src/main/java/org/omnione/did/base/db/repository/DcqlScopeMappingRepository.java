package org.omnione.did.base.db.repository;

import org.omnione.did.base.db.domain.DcqlScopeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DcqlScopeMappingRepository extends JpaRepository<DcqlScopeMapping, Long> {

    Optional<DcqlScopeMapping> findByScope(String scope);

    List<DcqlScopeMapping> findAllByEnabledTrue();
}
