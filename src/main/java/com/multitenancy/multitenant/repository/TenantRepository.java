package com.multitenancy.multitenant.repository;

import com.multitenancy.multitenant.model.TenantInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the master-db tenant registry.
 *
 * <p>Note: queries here always hit the MASTER datasource (no tenant context
 * required). The routing datasource falls back to master when no tenant is set.</p>
 */
@Repository
public interface TenantRepository extends JpaRepository<TenantInfo, Long> {

    Optional<TenantInfo> findByTenantId(String tenantId);

    @Query("SELECT t FROM TenantInfo t WHERE t.active = true")
    List<TenantInfo> findAllActive();

    boolean existsByTenantId(String tenantId);
}
