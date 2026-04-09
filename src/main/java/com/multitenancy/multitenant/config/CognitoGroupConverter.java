
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CognitoGroupConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        log.debug("Token signature verified successfully. Beginning claim extraction.");

        // 1. Extract Client ID and Set Tenant Context
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null && !clientId.trim().isEmpty()) {
             log.info("Authenticated request for Client ID: {}. Setting TenantContext.", clientId);
             TenantContext.setTenantId(clientId);
        } else {
             log.warn("Verified token is missing the 'client_id' claim. TenantContext will NOT be set.");
        }

        // 2. Extract Authorities
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt); 
        
        log.debug("Successfully converted JWT to AuthenticationToken with authorities: {}", authorities);
        return new JwtAuthenticationToken(jwt, authorities);
    }
    
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> cognitoGroups = jwt.getClaimAsStringList("cognito:groups");
        
        if (cognitoGroups == null || cognitoGroups.isEmpty()) {
            log.debug("No 'cognito:groups' found in token. Assigning empty authorities.");
            return new ArrayList<>();
        }
        
        List<SimpleGrantedAuthority> mappedAuthorities = cognitoGroups.stream()
                .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                .collect(Collectors.toList());
                
        log.debug("Mapped Cognito Groups {} to Spring Roles {}", cognitoGroups, mappedAuthorities);
        return mappedAuthorities;
    }
}

//------------------------------------------
package com.multitenancy.multitenant.config;


import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CognitoGroupConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. Get default authorities (like 'SCOPE_openid')
        Collection<GrantedAuthority> authorities = new HashSet<>(defaultAuthoritiesConverter.convert(jwt));

        // 2. Extract 'cognito:groups' from the JWT
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");

        // 3. Convert groups to Spring Security Roles (Prepend "ROLE_")
        if (groups != null) {
            Set<SimpleGrantedAuthority> groupAuthorities = groups.stream()
                    .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                    .collect(Collectors.toSet());
            authorities.addAll(groupAuthorities);
        }

        // 4. Return the fully populated Authentication token
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
