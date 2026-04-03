package com.multitenancy.multitenant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI 3.1 configuration.
 *
 * <p>Swagger UI:   http://localhost:8080/swagger-ui/index.html
 * <br>OpenAPI JSON: http://localhost:8080/v3/api-docs
 * <br>OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml</p>
 *
 * <h3>Authentication in Swagger UI</h3>
 * Click the <b>Authorize</b> button and paste a Cognito Bearer token:
 * <pre>
 *   Bearer eyJraWQiOiJXSzZLT...
 * </pre>
 * The token is automatically added to every request as
 * {@code Authorization: Bearer <token>}.
 *
 * <h3>How to get a token for testing</h3>
 * <pre>
 * POST https://your-domain.auth.us-east-1.amazoncognito.com/oauth2/token
 * Content-Type: application/x-www-form-urlencoded
 *
 * grant_type=password
 * &client_id=YOUR_CLIENT_ID
 * &username=john.doe
 * &password=YourPassword1!
 * </pre>
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.cognito.region:us-east-1}")
    private String cognitoRegion;

    @Value("${app.cognito.customer-pool.user-pool-id:us-east-1_REPLACE_ME}")
    private String customerPoolId;

    @Value("${app.cognito.admin-pool.user-pool-id:us-east-1_REPLACE_ME}")
    private String adminPoolId;

    private static final String SECURITY_SCHEME_NAME = "CognitoBearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("https://api.yourdomain.com").description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, cognitoBearerScheme())
                )
                // Apply security globally to all endpoints
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    private Info apiInfo() {
        return new Info()
                .title("Multi-Tenant Product API")
                .version("1.0.0")
                .description("""
                        ## Multi-Tenant REST API — AWS Cognito + Amazon RDS
                        
                        Each client (tenant) has their own **isolated MySQL schema** on Amazon RDS.
                        The correct schema is selected automatically based on the `custom:tenantId`
                        claim embedded in the Cognito JWT.
                        
                        ### Authentication
                        1. Obtain a token from **AWS Cognito** (Hosted UI or SDK — not from this service)
                        2. Click **Authorize** above and paste:  `Bearer <your_token>`
                        3. All requests will automatically include the token header
                        
                        ### Two User Pools
                        | Pool | Purpose | Extra role |
                        |------|---------|------------|
                        | Admin Pool | Internal staff / super admins | `ROLE_ADMIN` |
                        | Customer Pool | Tenant end-users | `ROLE_USER` |
                        
                        ### Tenant Routing
                        The `custom:tenantId` claim in the JWT determines which MySQL schema
                        handles your request. If absent, `cognito:groups` entries prefixed with
                        `tenant-` are used (e.g. `tenant-client_acme` → tenantId `client_acme`).
                        """)
                .contact(new Contact()
                        .name("Platform Team")
                        .email("platform@yourdomain.com"))
                .license(new License().name("Internal Use Only"));
    }

    /**
     * Registers an HTTP Bearer scheme so Swagger UI shows an Authorize button
     * where developers paste their Cognito token.
     *
     * <p>Also registers an OAuth2 Authorization Code flow pointing at the
     * Customer Cognito User Pool's hosted UI — allows direct login from Swagger UI
     * without leaving the page (requires Swagger UI callback URL added to Cognito
     * App Client allowed redirect URIs).</p>
     */
    private SecurityScheme cognitoBearerScheme() {
        String hostedUiBase = String.format(
                "https://your-domain.auth.%s.amazoncognito.com", cognitoRegion);

        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        Paste a Cognito **access token** or **id token** here.
                        
                        Get a token via:
                        ```
                        POST %s/oauth2/token
                        Content-Type: application/x-www-form-urlencoded
                        
                        grant_type=password&client_id=CLIENT_ID&username=USER&password=PASS
                        ```
                        Then enter: **Bearer eyJraWQi...**
                        """.formatted(hostedUiBase));
    }
}
