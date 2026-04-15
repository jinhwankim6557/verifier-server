package org.omnione.did.verifier.v1.oid4vp.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.base.db.domain.Oid4vpConfig;
import org.omnione.did.base.db.repository.Oid4vpConfigRepository;
import org.omnione.did.oid4vc.oid4vp.dto.OID4VPConfigDto;
import org.omnione.did.oid4vc.oid4vp.repository.OID4VPRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * SDK OID4VPRepository의 JPA 기반 구현체.
 * InMemory 대신 DB를 직접 조회하여 SDK의 VerifierConfigService에 데이터를 제공한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JpaOid4vpRepositoryAdapter implements OID4VPRepository {

    private final Oid4vpConfigRepository jpaRepository;

    @Override
    public Optional<OID4VPConfigDto> findByType(String type) {
        return jpaRepository.findByType(type).map(this::toDto);
    }

    @Override
    public Optional<OID4VPConfigDto> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDto);
    }

    @Override
    public List<OID4VPConfigDto> findAll() {
        return jpaRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public OID4VPConfigDto save(OID4VPConfigDto dto) {
        Oid4vpConfig entity = jpaRepository.findById(dto.getId() != null ? dto.getId() : 0L)
                .orElse(new Oid4vpConfig());

        entity.setType(dto.getType());
        entity.setConfig(dto.getConfig());

        Oid4vpConfig saved = jpaRepository.save(entity);
        return toDto(saved);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByType(String type) {
        return jpaRepository.findByType(type).isPresent();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private OID4VPConfigDto toDto(Oid4vpConfig entity) {
        return OID4VPConfigDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .config(entity.getConfig())
                .createdAt(entity.getCreatedAt() != null
                        ? LocalDateTime.ofInstant(entity.getCreatedAt(), ZoneId.systemDefault()) : null)
                .updatedAt(entity.getUpdatedAt() != null
                        ? LocalDateTime.ofInstant(entity.getUpdatedAt(), ZoneId.systemDefault()) : null)
                .build();
    }
}
