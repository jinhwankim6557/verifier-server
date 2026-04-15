package org.omnione.did.verifier.v1.admin.dto;

import lombok.*;
import org.omnione.did.base.db.domain.DcqlScopeMapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DcqlScopeMappingDTO {
    private Long id;
    private String scope;
    private String dcqlQuery;
    private String description;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;

    public static DcqlScopeMappingDTO toDTO(DcqlScopeMapping entity) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return DcqlScopeMappingDTO.builder()
                .id(entity.getId())
                .scope(entity.getScope())
                .dcqlQuery(entity.getDcqlQuery())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(formatInstant(entity.getCreatedAt(), formatter))
                .updatedAt(formatInstant(entity.getUpdatedAt(), formatter))
                .build();
    }

    private static String formatInstant(Instant instant, DateTimeFormatter formatter) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(formatter);
    }
}
