package com.multitenancy.multitenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product entity – replicated in EVERY tenant schema.
 *
 * <p>DDL for each tenant schema:
 * <pre>
 * CREATE TABLE products (
 *     id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
 *     name        VARCHAR(255)    NOT NULL,
 *     description TEXT,
 *     price       DECIMAL(19,2)   NOT NULL,
 *     quantity    INT             NOT NULL DEFAULT 0,
 *     category    VARCHAR(100),
 *     active      BOOLEAN         NOT NULL DEFAULT TRUE,
 *     created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *     INDEX idx_products_category (category),
 *     INDEX idx_products_name (name)
 * );
 * </pre>
 * </p>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 0;

    @Column(length = 100)
    private String category;

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
