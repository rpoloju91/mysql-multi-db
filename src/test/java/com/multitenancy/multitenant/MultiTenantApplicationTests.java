package com.multitenancy.multitenant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MultiTenantApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context starts without errors
    }
}
