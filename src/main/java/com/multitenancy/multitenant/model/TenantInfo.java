package com.multitenancy.multitenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted in the MASTER database – maps each logical tenant to its MySQL schema.
 *
 * <p>This table must exist in {@code master_db}:
 * <pre>
 * CREATE TABLE tenants (
 *     id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
 *     tenant_id   VARCHAR(50)  NOT NULL UNIQUE,
 *     schema_name VARCHAR(100) NOT NULL,
 *     active      BOOLEAN      NOT NULL DEFAULT TRUE,
 *     created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * );
 * </pre>
 * </p>
 */
@Entity
@Table(name = "tenants_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50, unique = true)
    private String tenantId;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
