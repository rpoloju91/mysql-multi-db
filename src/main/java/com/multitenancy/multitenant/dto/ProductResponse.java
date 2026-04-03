package com.multitenancy.multitenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Product data returned from the API")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    @Schema(description = "Unique product identifier", example = "1")
    private Long id;

    @Schema(description = "Product name", example = "Laptop Pro 15")
    private String name;

    @Schema(description = "Product description", example = "High-performance laptop with 16GB RAM")
    private String description;

    @Schema(description = "Product price", example = "1299.99")
    private BigDecimal price;

    @Schema(description = "Available stock quantity", example = "50")
    private int quantity;

    @Schema(description = "Product category", example = "Electronics")
    private String category;

    @Schema(description = "Whether the product is active", example = "true")
    private boolean active;

    @Schema(description = "Creation timestamp", example = "2026-04-02T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp", example = "2026-04-02T15:45:00")
    private LocalDateTime updatedAt;
}
