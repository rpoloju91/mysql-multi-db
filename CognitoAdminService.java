package com.hlt.api.service.serviceimpl;

import com.hlt.api.dtos.request.TicketRequest;
import com.hlt.api.model.ClientUser;
import com.hlt.api.repository.ClientUserRepository;
import com.hlt.api.service.IAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CognitoAdminService {

    private final ClientUserRepository clientUserRepository;

    @Qualifier("adminCognitoClient")
    private final CognitoIdentityProviderClient cognitoClient;

    private final OtpAuthService otpAuthService;

    private final IAuthService authService;

    @Value("${cognito.admin-user-pool-id}")
    private String adminUserPoolId;

    @Value("${cognito.admin-client-id}")
    private String adminClientId;

    @Value("${cognito.admin-client-secret}")
    private String adminClientSecret;

    /**
     * ============================================================
     * CREATE CLIENT USER (ADMIN CREATES USER)
     * ============================================================
     *
     * FLOW:
     * 1. Admin already logged in
     * 2. Admin sessionKey passed here
     * 3. Create Cognito user
     * 4. Save DB profile
     * 5. Send OTP
     * 6. NO USER SESSION CREATED HERE
     */
    @Transactional
    public AdminCreateUserResponse createClientUser(
            String type,
            String email,
            String phone,
            String firstName,
            String lastName,
            String adminSessionKey,
            String adminCognitoUsername
    ) {

        validateProfileData(email, phone, firstName, lastName);

        email = email.trim().toLowerCase();
        phone = phone.trim();
        firstName = firstName.trim();
        lastName = lastName.trim();

        // Duplicate validation
        if (clientUserRepository.existsByEmailAddressOrPhoneNumber(email, phone)) {
            throw new IllegalArgumentException("User already exists with email or phone.");
        }

        // Get logged-in admin details
        AdminGetUserResponse adminUserResponse = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                        .userPoolId(adminUserPoolId)
                        .username(adminCognitoUsername)
                        .build()
        );

        String createdByAdminEmail = adminUserResponse.userAttributes().stream()
                .filter(attr -> "email".equals(attr.name()))
                .findFirst()
                .map(AttributeType::value)
                .orElseThrow(() -> new RuntimeException("Admin email not found"));

        // Build user attributes
        List<AttributeType> userAttributes = new ArrayList<>();
        userAttributes.add(AttributeType.builder().name("email").value(email).build());
        userAttributes.add(AttributeType.builder().name("email_verified").value("true").build());
        userAttributes.add(AttributeType.builder().name("given_name").value(firstName).build());
        userAttributes.add(AttributeType.builder().name("family_name").value(lastName).build());
        userAttributes.add(AttributeType.builder().name("phone_number").value(phone).build());

        // Temporary password / OTP
        String temporaryPassword = otpAuthService.generateOtp();

        AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(adminUserPoolId)
                .username(email)
                .temporaryPassword(temporaryPassword)
                .userAttributes(userAttributes)
                .messageAction(MessageActionType.SUPPRESS)
                .build();

        AdminCreateUserResponse createUserResponse = null;

        try {

            // Step 1: Create Cognito User
            createUserResponse = cognitoClient.adminCreateUser(createUserRequest);

            // Step 2: Fetch actual Cognito SUB
            AdminGetUserResponse createdUserResponse = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(adminUserPoolId)
                            .username(email)
                            .build()
            );

            String cognitoSub = createdUserResponse.userAttributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .findFirst()
                    .map(AttributeType::value)
                    .orElseThrow(() -> new RuntimeException("Cognito SUB not found"));

            // Step 3: Save in DB
            ClientUser clientUser = new ClientUser();
            clientUser.setCognitoUserPoolId(adminUserPoolId);
            clientUser.setCognitoUserId(cognitoSub);
            clientUser.setEmailAddress(email);
            clientUser.setPhoneNumber(phone);
            clientUser.setFirstName(firstName);
            clientUser.setLastName(lastName);
            clientUser.setIsActive(1);
            clientUser.setCreateSessionKey(adminSessionKey);

            clientUserRepository.save(clientUser);

            // Step 4: Send OTP manually
            otpAuthService.sendEmail(email, null, temporaryPassword);

            log.info("Client user created successfully by admin: {}", createdByAdminEmail);

            return createUserResponse;

        } catch (Exception ex) {

            log.error("User creation failed, rolling back Cognito user. Error: {}", ex.getMessage());

            // Rollback Cognito if DB fails
            try {
                cognitoClient.adminDeleteUser(
                        AdminDeleteUserRequest.builder()
                                .userPoolId(adminUserPoolId)
                                .username(email)
                                .build()
                );
            } catch (Exception rollbackEx) {
                log.error("Rollback failed for Cognito user: {}", rollbackEx.getMessage());
            }

            throw new RuntimeException("Failed to create client user: " + ex.getMessage(), ex);
        }
    }

    /**
     * ============================================================
     * USER FIRST LOGIN / OTP VERIFICATION
     * ============================================================
     *
     * FLOW:
     * 1. User enters OTP
     * 2. Verify Cognito challenge
     * 3. Generate stable sessionKey (origin_jti preferred)
     * 4. Start DB Session
     */
    public Map<String, Object> verifyFirstLoginAndStartSession(
            String email,
            String otp,
            String sessionKey,
            String queryString
    ) {

        try {

            // Step 1: Initiate auth
            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(adminUserPoolId)
                            .clientId(adminClientId)
                            .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                            .authParameters(Map.of(
                                    "USERNAME", email,
                                    "PASSWORD", otp
                            ))
                            .build()
            );

            // Step 2: Get Cognito user
            AdminGetUserResponse userResponse = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(adminUserPoolId)
                            .username(email)
                            .build()
            );

            String cognitoSub = userResponse.userAttributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .findFirst()
                    .map(AttributeType::value)
                    .orElseThrow();

            // Step 3: Start DB session
            TicketRequest ticketRequest = new TicketRequest();
            ticketRequest.setSessionKey(sessionKey);
            ticketRequest.setCognitoUserId(cognitoSub);
            ticketRequest.setQueryString(queryString);

            authService.startSession(ticketRequest);

            // Step 4: Return tokens
            return Map.of(
                    "message", "Login successful",
                    "sessionKey", sessionKey,
                    "accessToken", authResponse.authenticationResult().accessToken(),
                    "refreshToken", authResponse.authenticationResult().refreshToken(),
                    "idToken", authResponse.authenticationResult().idToken()
            );

        } catch (Exception ex) {
            log.error("OTP verification failed: {}", ex.getMessage());
            throw new RuntimeException("Login failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * ============================================================
     * GET CLIENT USER
     * ============================================================
     */
    public Map<String, Object> getClientUser(String email) {

        AdminGetUserResponse response = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                        .userPoolId(adminUserPoolId)
                        .username(email)
                        .build()
        );

        Map<String, String> attributes = response.userAttributes()
                .stream()
                .collect(Collectors.toMap(
                        AttributeType::name,
                        AttributeType::value
                ));

        return Map.of(
                "username", response.username(),
                "status", response.userStatusAsString(),
                "enabled", response.enabled(),
                "attributes", attributes
        );
    }

    /**
     * ============================================================
     * DELETE CLIENT USER
     * ============================================================
     */
    public void deleteClientUser(String email) {

        cognitoClient.adminDeleteUser(
                AdminDeleteUserRequest.builder()
                        .userPoolId(adminUserPoolId)
                        .username(email)
                        .build()
        );

        log.info("Client user deleted successfully: {}", email);
    }

    /**
     * ============================================================
     * VALIDATIONS
     * ============================================================
     */
    private static void validateProfileData(
            String email,
            String phone,
            String firstName,
            String lastName
    ) {

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (!email.trim().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required.");
        }

        if (!phone.trim().matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("Invalid phone format. Use E.164 format.");
        }

        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required.");
        }

        if (!firstName.trim().matches("^[a-zA-Z\\s'\\-]{1,100}$")) {
            throw new IllegalArgumentException("Invalid first name.");
        }

        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required.");
        }

        if (!lastName.trim().matches("^[a-zA-Z\\s'\\-]{1,100}$")) {
            throw new IllegalArgumentException("Invalid last name.");
        }
    }
}
