package org.omnione.did.base.db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Entity
@Table(name = "oid4vp_session_mapping")
public class Oid4vpSessionMapping implements Serializable {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tx_id", nullable = false, length = 40)
    private String txId;
    @Column(name = "oid4vp_transaction_id", nullable = false, length = 100)
    private String oid4vpTransactionId;
    @Column(name = "oid4vp_request_id", nullable = false, length = 100)
    private String oid4vpRequestId;
    @Column(name = "state", nullable = false, length = 100)
    private String state;
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
