package com.hlt.api.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hlt.api.dao.AuthDao;
import com.hlt.api.dtos.request.SendEmailOtp;
import com.hlt.api.dtos.request.TicketRequest;
import com.hlt.api.dtos.request.VerifyEmailOtp;
import com.hlt.api.model.ClientUser;
import com.hlt.api.repository.ClientUserRepository;
import com.hlt.api.service.IAdminService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpAuthService {

    private final JavaMailSender mailSender;
    private final CognitoIdentityProviderClient cognitoClient;
    private final IAdminService adminService;
    private final AuthDao authDao;
    private final ClientUserRepository clientUserRepository;

    private final Cache<String, String> otpCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100000)
            .build();

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${cognito.user-pool-id}")
    private String userPoolId;

    @Value("${cognito.client-id}")
    private String clientId;

    @Value("${cognito.client-secret}")
    private String clientSecret;

    @Value("${cognito.hosted-ui-url}")
    private String hostedUiUrl;

    @Value("${cognito.redirect-uri}")
    private String redirectUri;

    @Value("${spring.mail.fromEmail}")
    private String fromMail;

    public OtpAuthService(
            @Qualifier("customerCognitoClient") CognitoIdentityProviderClient cognitoClient,
            JavaMailSender mailSender,
            IAdminService adminService,
            AuthDao authDao,
            ClientUserRepository clientUserRepository
    ) {
        this.mailSender = mailSender;
        this.cognitoClient = cognitoClient;
        this.adminService = adminService;
        this.authDao = authDao;
        this.clientUserRepository = clientUserRepository;
    }

    /*
     * ============================================================
     * STEP 1: SEND OTP
     * ============================================================
     */
    public void generateAndSendOtp(SendEmailOtp request) {

        validateEmail(request.getEmail());

        String email = request.getEmail().trim().toLowerCase();

        String otp = generateOtp();

        otpCache.put(email, otp);

        sendEmail(email, otp, null);

        log.info("OTP sent successfully to {}", email);
    }

    /*
     * ============================================================
     * STEP 2: VERIFY OTP + LOGIN + SESSION START
     * ============================================================
     *
     * FLOW:
     * Check OTP
     * Create Cognito user if absent
     * Authenticate
     * Check DB profile
     * If new user → guest + upsert
     * Start customer session
     */
    @Transactional
    public Map<String, Object> verifyOtpAndLogin(VerifyEmailOtp request) {

        validateVerifyRequest(request);

        String email = request.getEmail().trim().toLowerCase();
        String otp = request.getOtp().trim();

        /*
         * STEP 1: OTP CACHE VALIDATION
         */
        String cachedOtp = otpCache.getIfPresent(email);

        if (cachedOtp == null || !cachedOtp.equals(otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        otpCache.invalidate(email);

        /*
         * STEP 2: COGNITO CHECK / AUTO REGISTER
         */
        ensureCustomerExistsInCognito(email, otp);

        /*
         * STEP 3: LOGIN TO COGNITO
         */
        AdminInitiateAuthResponse authResponse = initiateAuth(email, otp);

        AuthenticationResultType authResult = authResponse.authenticationResult();

        /*
         * STEP 4: STABLE SESSION KEY
         * BEST PRACTICE:
         * origin_jti preferred
         */
        String sessionKey = extractStableSessionKey(authResult.accessToken());

        /*
         * STEP 5: CHECK USER EXISTS IN DB
         */
        boolean isNewUser = isNewCustomer(email);

        /*
         * STEP 6: NEW USER PROFILE BOOTSTRAP
         */
        if (isNewUser) {

            authDao.startGuestSession(sessionKey, "CUSTOMER_GUEST");

            TicketRequest upsertRequest = new TicketRequest();
            upsertRequest.setType("CUSTOMER");
            upsertRequest.setSessionKey(sessionKey);
            upsertRequest.setCognitoUserId(email);
            upsertRequest.setEmail(email);
            upsertRequest.setPhone(null);
            upsertRequest.setFirstName(null);
            upsertRequest.setLastName(null);

            /*
             * IMPORTANT:
             * Your existing proc requires permission,
             * so use registration proc if available,
             * else bypass permission for first profile creation.
             */
            adminService.upsertUser(upsertRequest);
        }

        /*
         * STEP 7: START CUSTOMER SESSION
         */
        authDao.startClientCustomerSession(
                email,
                sessionKey,
                "CUSTOMER_LOGIN",
                null
        );

        /*
         * STEP 8: PROFILE CHECK
         */
        boolean isProfileComplete = checkProfileCompletion(email);

        /*
         * STEP 9: RESPONSE
         */
        return Map.of(
                "status", "SUCCESS",
                "accessToken", authResult.accessToken(),
                "idToken", authResult.idToken(),
                "refreshToken", authResult.refreshToken(),
                "expiresIn", authResult.expiresIn(),
                "tokenType", authResult.tokenTypeAsString(),
                "sessionKey", sessionKey,
                "cognitoUserId", email,
                "isNewUser", isNewUser,
                "isProfileComplete", isProfileComplete
        );
    }

    /*
     * ============================================================
     * ENSURE USER EXISTS IN COGNITO
     * ============================================================
     */
    private void ensureCustomerExistsInCognito(String email, String otp) {

        try {

            cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(email)
                            .build()
            );

            cognitoClient.adminSetUserPassword(
                    AdminSetUserPasswordRequest.builder()
                            .userPoolId(userPoolId)
                            .username(email)
                            .password(otp)
                            .permanent(true)
                            .build()
            );

        } catch (UserNotFoundException ex) {

            autoRegisterCustomer(email, otp);
        }
    }

    /*
     * ============================================================
     * AUTO REGISTER CUSTOMER
     * ============================================================
     */
    private void autoRegisterCustomer(String email, String otp) {

        cognitoClient.adminCreateUser(
                AdminCreateUserRequest.builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .userAttributes(
                                AttributeType.builder().name("email").value(email).build(),
                                AttributeType.builder().name("email_verified").value("true").build()
                        )
                        .messageAction(MessageActionType.SUPPRESS)
                        .build()
        );

        cognitoClient.adminAddUserToGroup(
                AdminAddUserToGroupRequest.builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .groupName("CUSTOMERUSER")
                        .build()
        );

        cognitoClient.adminSetUserPassword(
                AdminSetUserPasswordRequest.builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .password(otp)
                        .permanent(true)
                        .build()
        );

        log.info("Auto registered new customer {}", email);
    }

    /*
     * ============================================================
     * AUTHENTICATE
     * ============================================================
     */
    private AdminInitiateAuthResponse initiateAuth(String email, String otp) {

        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", email);
        authParams.put("PASSWORD", otp);
        authParams.put("SECRET_HASH", calculateSecretHash(email));

        return cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                        .userPoolId(userPoolId)
                        .clientId(clientId)
                        .authParameters(authParams)
                        .build()
        );
    }

    /*
     * ============================================================
     * SESSION KEY
     * ============================================================
     */
    public String extractStableSessionKey(String accessToken) {

        try {

            String payload = accessToken.split("\\.")[1];

            String decoded = new String(
                    Base64.getUrlDecoder().decode(payload),
                    StandardCharsets.UTF_8
            );

            ObjectMapper mapper = new ObjectMapper();

            Map<?, ?> claims = mapper.readValue(decoded, Map.class);

            Object originJti = claims.get("origin_jti");

            if (originJti != null) {
                return originJti.toString();
            }

            return claims.get("jti").toString();

        } catch (Exception ex) {

            log.warn("Could not extract origin_jti, fallback to UUID");

            return UUID.randomUUID().toString();
        }
    }

    /*
     * ============================================================
     * PROFILE STATUS
     * ============================================================
     */
    private boolean checkProfileCompletion(String email) {

        Optional<ClientUser> user = clientUserRepository.findByCognitoUserId(email);

        if (user.isEmpty()) {
            return false;
        }

        ClientUser clientUser = user.get();

        return StringUtils.isNotBlank(clientUser.getFirstName())
                && StringUtils.isNotBlank(clientUser.getLastName());
    }

    /*
     * ============================================================
     * NEW USER CHECK
     * ============================================================
     */
    private boolean isNewCustomer(String email) {

        return !clientUserRepository.existsByCognitoUserId(email);
    }

    /*
     * ============================================================
     * OTP GENERATION
     * ============================================================
     */
    public String generateOtp() {

        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%";
        String all = upper + lower + digits + special;

        StringBuilder otp = new StringBuilder();

        otp.append(upper.charAt(secureRandom.nextInt(upper.length())));
        otp.append(lower.charAt(secureRandom.nextInt(lower.length())));
        otp.append(digits.charAt(secureRandom.nextInt(digits.length())));
        otp.append(special.charAt(secureRandom.nextInt(special.length())));

        for (int i = 0; i < 4; i++) {
            otp.append(all.charAt(secureRandom.nextInt(all.length())));
        }

        List<Character> chars = new ArrayList<>();

        for (char c : otp.toString().toCharArray()) {
            chars.add(c);
        }

        Collections.shuffle(chars, secureRandom);

        StringBuilder finalOtp = new StringBuilder();

        for (char c : chars) {
            finalOtp.append(c);
        }

        return finalOtp.toString();
    }

    /*
     * ============================================================
     * EMAIL
     * ============================================================
     */
    public void sendEmail(String toEmail, String otp, String tempPassword) {

        String subject;
        String bodyHtml;

        if (StringUtils.isNotBlank(tempPassword)) {

            subject = "Welcome - Account Created";

            bodyHtml = "<html><body>"
                    + "<h3>Welcome!</h3>"
                    + "<p>Username: <b>" + toEmail + "</b></p>"
                    + "<p>Temporary Password: <b>" + tempPassword + "</b></p>"
                    + "</body></html>";

        } else {

            subject = "Your Login OTP";

            bodyHtml = "<html><body>"
                    + "<h3>Your Login OTP</h3>"
                    + "<p>Your OTP is: <b>" + otp + "</b></p>"
                    + "<p>Expires in 5 minutes.</p>"
                    + "</body></html>";
        }

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromMail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);

            mailSender.send(message);

        } catch (MessagingException ex) {

            throw new RuntimeException("Failed to send email", ex);
        }
    }

    /*
     * ============================================================
     * SECRET HASH
     * ============================================================
     */
    private String calculateSecretHash(String username) {

        try {

            String message = username + clientId;

            SecretKeySpec signingKey = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(signingKey);

            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(rawHmac);

        } catch (Exception ex) {

            throw new RuntimeException("Error calculating secret hash", ex);
        }
    }

    /*
     * ============================================================
     * VALIDATIONS
     * ============================================================
     */
    private void validateEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email required");
        }

        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email");
        }
    }

    private void validateVerifyRequest(VerifyEmailOtp request) {

        validateEmail(request.getEmail());

        if (request.getOtp() == null || request.getOtp().isBlank()) {
            throw new IllegalArgumentException("OTP required");
        }
    }
}
