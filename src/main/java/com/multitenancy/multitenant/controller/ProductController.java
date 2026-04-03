package com.multitenancy.multitenant.controller;

import com.multitenancy.multitenant.dto.ApiResponse;
import com.multitenancy.multitenant.dto.PagedResponse;
import com.multitenancy.multitenant.dto.ProductRequest;
import com.multitenancy.multitenant.dto.ProductResponse;
import com.multitenancy.multitenant.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Products", description = "Tenant-scoped product CRUD operations. The tenant is resolved automatically from the Cognito JWT — no manual tenant header required.")
@SecurityRequirement(name = "CognitoBearerAuth")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(
        summary = "Create a product",
        description = "Creates a new product in the calling tenant's database schema. The correct schema is selected automatically from the `custom:tenantId` claim in the Cognito token."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Product created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Created", value = """
                    {
                      "success": true,
                      "message": "Product created successfully",
                      "data": {
                        "id": 1,
                        "name": "Laptop Pro 15",
                        "description": "High-performance laptop with 16GB RAM and 512GB SSD",
                        "price": 1299.99,
                        "quantity": 50,
                        "category": "Electronics",
                        "active": true,
                        "createdAt": "2026-04-02T10:30:00",
                        "updatedAt": "2026-04-02T10:30:00"
                      },
                      "timestamp": "2026-04-02T10:30:00"
                    }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    { "success": false, "message": "Name is required", "timestamp": "2026-04-02T10:30:00" }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid Cognito token")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Product to create",
                required = true,
                content = @Content(examples = @ExampleObject(name = "Electronics", value = """
                    {
                      "name": "Laptop Pro 15",
                      "description": "High-performance laptop with 16GB RAM and 512GB SSD",
                      "price": 1299.99,
                      "quantity": 50,
                      "category": "Electronics"
                    }""")))
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @Operation(summary = "Get product by ID", description = "Returns a single active product belonging to the calling tenant.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "data": {
                        "id": 1,
                        "name": "Laptop Pro 15",
                        "price": 1299.99,
                        "quantity": 50,
                        "category": "Electronics",
                        "active": true,
                        "createdAt": "2026-04-02T10:30:00",
                        "updatedAt": "2026-04-02T10:30:00"
                      },
                      "timestamp": "2026-04-02T10:30:00"
                    }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(examples = @ExampleObject(value = """
                    { "success": false, "message": "Product not found with id: 99", "timestamp": "2026-04-02T10:30:00" }""")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @Operation(
        summary = "List all products (paginated)",
        description = "Returns paginated list of active products for the calling tenant."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Products listed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "data": {
                        "content": [
                          { "id": 1, "name": "Laptop Pro 15", "price": 1299.99, "category": "Electronics", "active": true },
                          { "id": 2, "name": "Wireless Mouse", "price": 29.99,   "category": "Accessories",  "active": true }
                        ],
                        "page": 0,
                        "size": 20,
                        "totalElements": 2,
                        "totalPages": 1,
                        "last": true
                      },
                      "timestamp": "2026-04-02T10:30:00"
                    }""")))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getAll(
            @Parameter(description = "Page number (0-based)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "name")         @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction: asc or desc")          @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getAll(page, size, sortBy, direction)));
    }

    @Operation(summary = "Get products by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> getByCategory(
            @Parameter(description = "Category name", example = "Electronics") @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getByCategory(category, page, size)));
    }

    @Operation(
        summary = "Search products",
        description = "Case-insensitive search across product name and category."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "data": {
                        "content": [
                          { "id": 1, "name": "Laptop Pro 15", "category": "Electronics", "price": 1299.99 }
                        ],
                        "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "last": true
                      },
                      "timestamp": "2026-04-02T10:30:00"
                    }""")))
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> search(
            @Parameter(description = "Search keyword", example = "laptop") @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.search(keyword, page, size)));
    }

    @Operation(summary = "Update a product", description = "Full update of an existing product in the calling tenant's schema.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Product updated successfully", productService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a product", description = "Marks the product as inactive. The record is retained in the database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product deleted",
            content = @Content(examples = @ExampleObject(value = """
                    { "success": true, "message": "Product deleted successfully", "timestamp": "2026-04-02T10:30:00" }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
