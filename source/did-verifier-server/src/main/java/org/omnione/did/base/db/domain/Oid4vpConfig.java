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
@Table(name = "oid4vp_config")
public class Oid4vpConfig extends BaseEntity implements Serializable {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "type", nullable = false, length = 50)
    @Builder.Default
    private String type = "OID4VP";
    @Column(name = "config", nullable = false, columnDefinition = "text")
    private String config;
}
