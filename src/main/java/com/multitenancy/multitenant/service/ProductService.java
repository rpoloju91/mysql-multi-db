package com.multitenancy.multitenant.service;

import com.multitenancy.multitenant.dto.PagedResponse;
import com.multitenancy.multitenant.exception.ResourceNotFoundException;
import com.multitenancy.multitenant.dto.ProductRequest;
import com.multitenancy.multitenant.dto.ProductResponse;
import com.multitenancy.multitenant.model.Product;
import com.multitenancy.multitenant.repository.ProductRepository;
import com.multitenancy.multitenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for product CRUD.
 *
 * <p>Every method runs inside a transaction; Spring routes the transaction
 * to the DataSource mapped to the current {@link TenantContext}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String tenantId = TenantContext.getTenantId();
        log.info("Creating product for tenant={} name={}", tenantId, request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .category(request.getCategory())
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getAll(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductResponse> result = productRepository
                .findAllByActiveTrue(pageable)
                .map(this::toResponse);
        return PagedResponse.of(result);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return PagedResponse.of(
                productRepository.findByCategoryAndActiveTrue(category, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> search(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PagedResponse.of(
                productRepository.searchByNameOrCategory(keyword, pageable)
                        .map(this::toResponse));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity());
        product.setCategory(request.getCategory());

        return toResponse(productRepository.save(product));
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsByIdAndActiveTrue(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        int rows = productRepository.softDeleteById(id);
        log.info("Soft-deleted product id={} tenant={} rows={}", id, TenantContext.getTenantId(), rows);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .quantity(p.getQuantity())
                .category(p.getCategory())
                .active(p.isActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
