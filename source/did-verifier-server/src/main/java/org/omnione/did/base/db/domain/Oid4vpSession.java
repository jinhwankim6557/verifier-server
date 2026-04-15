package org.omnione.did.base.db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "oid4vp_session")
public class Oid4vpSession implements Serializable {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "nonce", length = 200)
    private String nonce;

    @Column(name = "dcql_query", columnDefinition = "text")
    private String dcqlQuery;

    @Column(name = "response_mode", length = 50)
    private String responseMode;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "CREATED";

    @Column(name = "client_metadata", columnDefinition = "text")
    private String clientMetadata;

    @Column(name = "request_uri_fetched_at")
    private Long requestUriFetchedAt;

    @Column(name = "vp_token", columnDefinition = "text")
    private String vpToken;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "expires_at")
    private Long expiresAt;

    @Column(name = "updated_at")
    private Long updatedAt;
}
