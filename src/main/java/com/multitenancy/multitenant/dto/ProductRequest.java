package com.multitenancy.multitenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Schema(description = "Request body for creating or updating a product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @Schema(description = "Product name", example = "Laptop Pro 15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Schema(description = "Detailed product description", example = "High-performance laptop with 16GB RAM and 512GB SSD")
    private String description;

    @Schema(description = "Product price (must be > 0)", example = "1299.99", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @Digits(integer = 17, fraction = 2, message = "Invalid price format")
    private BigDecimal price;

    @Schema(description = "Available stock quantity", example = "50")
    @Min(value = 0, message = "Quantity cannot be negative")
    private int quantity;

    @Schema(description = "Product category", example = "Electronics")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;
}
