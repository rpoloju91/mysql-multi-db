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