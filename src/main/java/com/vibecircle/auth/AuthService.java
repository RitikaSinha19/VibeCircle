
package com.vibecircle.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Optional;

import com.vibecircle.auth.dto.*;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.TokenUtil;
import com.vibecircle.config.AuthProperties;
import com.vibecircle.user.User;
import com.vibecircle.user.UserRepository;
import com.vibecircle.user.UserStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthProperties props;
    private final EmailService emailService;

    /**
     * Used when the supplied username does not exist.
     * Helps prevent timing differences from revealing valid accounts.
     */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            VerificationTokenRepository verificationTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            AuthProperties props,
            EmailService emailService) {

        this.users = users;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.emailService = emailService;

        this.dummyHash = encoder.encode("not-a-real-password");
    }

    // ------------------------------------------------------------
    // REGISTER
    // ------------------------------------------------------------

    @Transactional
    public RegisterResponse register(RegisterRequest req) {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        int age = Period.between(req.dateOfBirth(), today).getYears();

        if (age < props.minAge()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You must be at least " + props.minAge()
                            + " years old to join VibeCircle"
            );
        }

        if (age > 120) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date of birth"
            );
        }

        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();

        // Usernames must be unique.
        // Email addresses can be shared by multiple accounts.
        if (users.usernameTaken(username)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Username is already taken"
            );
        }

        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setDateOfBirth(req.dateOfBirth());

        user.setStatus(
                props.requireEmailVerification()
                        ? UserStatus.PENDING_VERIFICATION
                        : UserStatus.ACTIVE
        );

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Username is already taken"
            );
        }

        String message = "Account created";

        // Generate and email OTP when verification is enabled.
        if (props.requireEmailVerification()) {

            String otp = String.format(
                    "%06d",
                    RANDOM.nextInt(1_000_000)
            );

            VerificationToken verificationToken = new VerificationToken(
                    user.getId(),
                    VerificationType.EMAIL_VERIFY,
                    TokenUtil.sha256Hex(otp),
                    Instant.now().plus(VERIFY_TTL)
            );

            verificationTokens.save(verificationToken);

            // Send OTP to the registered email address.
            emailService.sendOtp(email, otp);

            message = "Account created. A verification OTP has been sent to your email.";
        }

        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                message
        );
    }

    // ------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest req) {

        String username = req.identifier().trim();

        // Multiple accounts can share one email.
        // Therefore, login must use a unique username.
        if (username.contains("@")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Please log in using your username because multiple accounts can share an email."
            );
        }

        Optional<User> found = users.findByUsername(username);

        String hash = found
                .map(User::getPasswordHash)
                .orElse(dummyHash);

        boolean passwordOk = encoder.matches(req.password(), hash);

        if (found.isEmpty() || !passwordOk) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        User user = found.get();

        switch (user.getStatus()) {

            case PENDING_VERIFICATION ->
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "Please verify your email first"
                    );

            case SUSPENDED ->
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "This account is suspended"
                    );

            case DELETED ->
                    throw new ApiException(
                            HttpStatus.UNAUTHORIZED,
                            "Invalid credentials"
                    );

            case ACTIVE -> {
                // Continue login.
            }
        }

        user.setLastSeenAt(Instant.now());

        return issueTokens(user);
    }

    // ------------------------------------------------------------
    // VERIFY EMAIL USING OTP
    // ------------------------------------------------------------

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(VerifyEmailRequest req) {

        Instant now = Instant.now();

        User user = users.findByUsername(req.username().trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid OTP or username"
                ));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "This account is already verified or cannot be verified."
            );
        }

        VerificationToken token = verificationTokens
                .findFirstByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                        user.getId(),
                        VerificationType.EMAIL_VERIFY
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "OTP not found. Please register again."
                ));

        if (!token.getExpiresAt().isAfter(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OTP has expired. Please request a new one."
            );
        }

        if (token.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new OTP."
            );
        }

        String submittedHash = TokenUtil.sha256Hex(req.otp());

        boolean otpMatches = MessageDigest.isEqual(
                token.getTokenHash().getBytes(StandardCharsets.UTF_8),
                submittedHash.getBytes(StandardCharsets.UTF_8)
        );

        if (!otpMatches) {

            token.incrementAttempts();

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Incorrect OTP. Please try again."
            );
        }

        // OTP is correct. Activate the account.
        token.setUsedAt(now);

        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);
    }

    // ------------------------------------------------------------
    // REFRESH TOKEN
    // ------------------------------------------------------------

    /**
     * noRollbackFor ensures that revoking every session after
     * refresh-token reuse is committed even though an exception is thrown.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest req) {

        Instant now = Instant.now();

        RefreshToken old = refreshTokens
                .findByTokenHashForUpdate(
                        TokenUtil.sha256Hex(req.refreshToken())
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid refresh token"
                ));

        if (old.getRevokedAt() != null) {

            // A revoked token was reused.
            // Revoke every session belonging to this user.
            refreshTokens.revokeAllForUser(old.getUserId(), now);

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid refresh token"
            );
        }

        if (old.getExpiresAt().isBefore(now)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token expired"
            );
        }

        User user = users.findById(old.getUserId())
                .filter(u ->
                        u.getStatus() == UserStatus.ACTIVE
                                && u.getDeletedAt() == null
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid refresh token"
                ));

        old.setRevokedAt(now);

        return issueTokens(user, old);
    }

    // ------------------------------------------------------------
    // LOGOUT
    // ------------------------------------------------------------

    @Transactional
    public void logout(RefreshRequest req) {

        refreshTokens
                .findByTokenHashForUpdate(
                        TokenUtil.sha256Hex(req.refreshToken())
                )
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));

        // Logging out twice, or with an invalid token, is not an error.
    }

    // ------------------------------------------------------------
    // TOKEN HELPERS
    // ------------------------------------------------------------

    private AuthResponse issueTokens(User user) {
        return issueTokens(user, null);
    }

    private AuthResponse issueTokens(
            User user,
            RefreshToken replaced
    ) {

        String rawRefresh = TokenUtil.newToken();

        RefreshToken fresh = refreshTokens.saveAndFlush(
                new RefreshToken(
                        user.getId(),
                        TokenUtil.sha256Hex(rawRefresh),
                        Instant.now().plus(props.refreshTokenTtl())
                )
        );

        if (replaced != null) {
            replaced.setReplacedBy(fresh.getId());
        }

        return new AuthResponse(
                jwt.createAccessToken(user),
                rawRefresh,
                "Bearer",
                jwt.accessTtlSeconds(),
                new AuthResponse.UserSummary(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getRole()
                )
        );
    }
}
