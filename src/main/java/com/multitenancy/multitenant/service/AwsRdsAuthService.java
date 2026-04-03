package com.multitenancy.multitenant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.RdsUtilities;

@Service
public class AwsRdsAuthService {

    @Value("${aws.region}")
    private String awsRegion;

    public String generateAuthToken(String hostname, int port, String username) {
        try (RdsClient rdsClient = RdsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            RdsUtilities utilities = rdsClient.utilities();
            return utilities.generateAuthenticationToken(builder -> builder
                    .hostname(hostname)
                    .port(port)
                    .username(username)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AWS RDS IAM token", e);
        }
    }
}