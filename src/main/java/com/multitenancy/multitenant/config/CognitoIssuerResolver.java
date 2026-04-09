

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CognitoIssuerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();
    private final CognitoGroupConverter converter = new CognitoGroupConverter();
    private final Set<String> trustedIssuers;
    private final ObjectMapper mapper = new ObjectMapper();

    public CognitoIssuerResolver(List<String> issuers) {
        this.trustedIssuers = new HashSet<>(issuers);
        log.info("CognitoIssuerResolver initialized with {} trusted issuers", issuers.size());
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        log.debug("Resolving AuthenticationManager for request: {}", request.getRequestURI());
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            log.warn("Authentication failed: Missing or malformed Authorization header");
            throw new JwtException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2) {
                log.warn("Authentication failed: JWT structure is invalid (missing payload)");
                throw new JwtException("Invalid JWT structure");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(chunks[1]));
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);

            String issuer = (String) payload.get("iss");
            log.debug("Extracted issuer from unverified token: {}", issuer);
            
            if (issuer == null || !trustedIssuers.contains(issuer)) {
                log.warn("SECURITY ALERT: Rejected token from untrusted issuer: {}", issuer);
                throw new JwtException("Untrusted or missing issuer: " + issuer);
            }

            // Route to the correct manager
            return managers.computeIfAbsent(issuer, key -> {
                log.info("First time seeing issuer [{}]. Creating and caching new AuthenticationManager...", key);
                return createManager(key);
            });
            
        } catch (JwtException e) {
            // Re-throw our specific exceptions without wrapping them
            throw e;
        } catch (Exception e) {
            log.error("Critical error while parsing unverified JWT payload", e);
            throw new JwtException("Token parsing failed", e);
        }
    }

    private AuthenticationManager createManager(String issuer) {
        log.debug("Fetching JWKS from Cognito for issuer: {}", issuer);
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }
}

//--------------------------------------
package com.multitenancy.multitenant.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.multitenancy.multitenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CognitoIssuerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();
    private final CognitoGroupConverter converter = new CognitoGroupConverter();
    private final Set<String> trustedIssuers;
    private final ObjectMapper mapper = new ObjectMapper();

    public CognitoIssuerResolver(List<String> issuers) {
        this.trustedIssuers = new HashSet<>(issuers);
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            throw new JwtException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            // Decode Payload to get Client ID and Issuer
            String[] chunks = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(chunks[1]));
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);

            String issuer = (String) payload.get("iss");
            if (!trustedIssuers.contains(issuer)) {
                throw new JwtException("Untrusted issuer: " + issuer);
            }

            // SET TENANT CONTEXT BASED ON CLIENT_ID
            if (payload.containsKey("client_id")) {
                String clientId = (String) payload.get("client_id");
                TenantContext.setTenantId(clientId);
            }

            return managers.computeIfAbsent(issuer, this::createManager);
        } catch (Exception e) {
            throw new JwtException("Token parsing failed", e);
        }
    }

    private AuthenticationManager createManager(String issuer) {
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }
}
