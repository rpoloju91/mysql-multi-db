package com.multitenancy.multitenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnsSameTenantId() {
        TenantContext.setTenantId("client_acme");
        assertThat(TenantContext.getTenantId()).isEqualTo("client_acme");
    }

    @Test
    void clear_removesValue() {
        TenantContext.setTenantId("client_acme");
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void setTenantId_blankThrows() {
        assertThatThrownBy(() -> TenantContext.setTenantId("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setTenantId_nullThrows() {
        assertThatThrownBy(() -> TenantContext.setTenantId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
