package com.multitenancy.multitenant.datasource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.multitenancy.multitenant.context.TenantContext;
import com.multitenancy.multitenant.service.AwsRdsAuthService;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

public class DynamicTenantDataSource extends AbstractRoutingDataSource {

    private final DataSource masterDataSource;
    private final AwsRdsAuthService rdsAuthService;

    // Cache: Evicts pools after 1 hour of inactivity to save MySQL connections
    private final Cache<String, DataSource> tenantCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .removalListener((String key, DataSource ds, RemovalCause cause) -> {
                if (ds instanceof HikariDataSource hds) {
                    hds.close();
                }
            })
            .build();

    public DynamicTenantDataSource(DataSource masterDataSource, AwsRdsAuthService rdsAuthService) {
        this.masterDataSource = masterDataSource;
        this.rdsAuthService = rdsAuthService;
        setDefaultTargetDataSource(masterDataSource);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String clientId = (String) determineCurrentLookupKey();
        if (clientId == null) return masterDataSource;

        return tenantCache.get(clientId, this::createDataSourceForTenant);
    }

    private DataSource createDataSourceForTenant(String clientId) {
        String sql = """
            SELECT c.host_name, c.username, c.port, c.tenant_id 
            FROM tenant_config c
            JOIN tenant_config_mapping m ON c.tenant_id = m.tenant_id
            WHERE m.cognito_app_client_id = ? AND c.active = 1
        """;

        try (Connection conn = masterDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String host = rs.getString("host_name");
                    String user = rs.getString("username");
                    int port = rs.getInt("port");
                    String dbName = rs.getString("tenant_id");

                    // Generate IAM Token as Password
                    String iamToken = rdsAuthService.generateAuthToken(host, port, user);

                    HikariDataSource ds = new HikariDataSource();
                    ds.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName);
                    ds.setUsername(user);
                    ds.setPassword(iamToken);

                    // High-Performance Pool Settings
                    ds.setMaximumPoolSize(5);
                    ds.setMinimumIdle(0);
                    ds.setMaxLifetime(600000); // 10 mins (IAM Token expires in 15)
                    ds.setPoolName(clientId + "-Pool");

                    return ds;
                }
                throw new RuntimeException("No mapping found for Client ID: " + clientId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Dynamic Routing Failed", e);
        }
    }
}