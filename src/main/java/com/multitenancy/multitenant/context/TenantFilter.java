package com.multitenancy.multitenant.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

@Component
public class TenantFilter extends OncePerRequestFilter {

    // Jackson mapper to read the JSON inside the token
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Look for the Bearer token in the request headers
        String authHeader = request.getHeader("Authorization");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // JWTs are split into 3 parts by periods (Header.Payload.Signature)
                String[] chunks = token.split("\\.");

                if (chunks.length > 1) {
                    // 2. Decode the Base64 payload (the middle part)
                    Base64.Decoder decoder = Base64.getUrlDecoder();
                    String payload = new String(decoder.decode(chunks[1]));

                    // 3. Parse the JSON payload
                    JsonNode jsonNode = objectMapper.readTree(payload);

                    // 4. Extract the tenant ID.
                    // Checking the most common Cognito/JWT variations!
                    String tenantId = null;
                    if (jsonNode.has("custom:tenantId")) {
                        tenantId = jsonNode.get("custom:tenantId").asText();
                    } else if (jsonNode.has("tenantId")) {
                        tenantId = jsonNode.get("tenantId").asText();
                    } else if (jsonNode.has("tenant_id")) {
                        tenantId = jsonNode.get("tenant_id").asText();
                    } else if (jsonNode.has("client_id")) { // Sometimes AWS puts it here
                        tenantId = jsonNode.get("client_id").asText();
                    }

                    // 5. Save the extracted tenant to the ThreadLocal context
                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContext.setTenantId(tenantId);
                        System.out.println("🔐 JWT Decoded successfully! Routing to Tenant: " + tenantId);
                    } else {
                        System.out.println("⚠️ Warning: Token was read, but no tenant identifier was found!");
                        System.out.println("RAW PAYLOAD DEBUG: " + payload);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to parse JWT for tenant routing: " + e.getMessage());
        }

        try {
            // Continue the request down the chain to the Controller
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Always clean up after the request finishes to prevent memory/data leaks
            TenantContext.clear();
        }
    }
}