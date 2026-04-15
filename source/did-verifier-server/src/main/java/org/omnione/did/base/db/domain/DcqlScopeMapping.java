package org.omnione.did.base.db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Entity
@Table(name = "dcql_scope_mapping")
public class DcqlScopeMapping extends BaseEntity implements Serializable {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scope", nullable = false, length = 100)
    private String scope;
    @Column(name = "dcql_query", nullable = false, columnDefinition = "text")
    private String dcqlQuery;
    @Column(name = "description", length = 500)
    private String description;
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
