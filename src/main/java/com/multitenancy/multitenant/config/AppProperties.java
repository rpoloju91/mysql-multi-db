package com.multitenancy.multitenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for all {@code app.*} properties in application.yml.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private MasterDatasource masterDatasource = new MasterDatasource();
    private TenantDatasource tenantDatasource = new TenantDatasource();
    private Aws aws = new Aws();
    private Cognito cognito = new Cognito();

    @Data
    public static class MasterDatasource {
        private String url;
        private String username;
        private String driverClassName;
        private Pool pool = new Pool();
    }

    @Data
    public static class TenantDatasource {
        private String urlTemplate;
        private String username;
        private String driverClassName;
        private Pool pool = new Pool();
    }

    @Data
    public static class Pool {
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private long connectionTimeout = 3000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
    }

    @Data
    public static class Aws {
        private Rds rds = new Rds();

        @Data
        public static class Rds {
            private String host;
            private int port = 3306;
            private String region = "us-east-1";
            private boolean useIamAuth = true;
            /** Plain-text password used ONLY when useIamAuth=false (local development). */
            private String localPassword;
        }
    }

    /**
     * AWS Cognito User Pool configuration.
     * Two separate pools: one for admin users, one for tenant customers.
     */
    @Data
    public static class Cognito {
        private String region = "us-east-1";
        private UserPool adminPool = new UserPool();
        private UserPool customerPool = new UserPool();

        /** JWT claim that carries the tenant identifier (custom attribute in Cognito). */
        private String tenantIdClaim = "custom:tenantId";

        /** JWT claim that lists Cognito group memberships. */
        private String groupsClaim = "cognito:groups";

        /**
         * Group name prefix used to derive tenantId when the custom claim is absent.
         * e.g.  group "tenant-client_acme"  →  tenantId "client_acme"
         */
        private String groupsTenantPrefix = "tenant-";

        /** Cognito group that grants ROLE_ADMIN. */
        private String adminGroupName = "Admins";

        @Data
        public static class UserPool {
            private String userPoolId;
            private String clientId;
        }

        /** Builds the Cognito issuer URI for this pool. */
        public String getAdminIssuerUri() {
            return String.format("https://cognito-idp.%s.amazonaws.com/%s",
                    region, adminPool.getUserPoolId());
        }

        /** Builds the Cognito issuer URI for this pool. */
        public String getCustomerIssuerUri() {
            return String.format("https://cognito-idp.%s.amazonaws.com/%s",
                    region, customerPool.getUserPoolId());
        }

        /** Builds the JWKS URI directly (avoids OIDC discovery round-trip at startup). */
        public String getAdminJwksUri() {
            return getAdminIssuerUri() + "/.well-known/jwks.json";
        }

        public String getCustomerJwksUri() {
            return getCustomerIssuerUri() + "/.well-known/jwks.json";
        }
    }
}
