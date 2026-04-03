package com.multitenancy.multitenant.config;


import com.multitenancy.multitenant.datasource.DynamicTenantDataSource;
import com.multitenancy.multitenant.service.AwsRdsAuthService;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Autowired
    private AwsRdsAuthService authService;

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String masterUser;

    @Value("${aws.rds.master.host}")
    private String masterHost;

    @Value("${aws.rds.master.port:3306}")
    private int masterPort;

    @Bean
    public DataSource dataSource() {
        // 1. Setup Master Pool (used to lookup tenant configs)
        HikariDataSource masterDs = new HikariDataSource();
        masterDs.setJdbcUrl(masterUrl);
        masterDs.setUsername(masterUser);

        // Initial IAM token for the Registry database
        masterDs.setPassword(authService.generateAuthToken(masterHost, masterPort, masterUser));

        masterDs.setPoolName("Master-Pool");
        masterDs.setMaxLifetime(600000);

        // 2. Return the Router
        return new DynamicTenantDataSource(masterDs, authService);
    }
}