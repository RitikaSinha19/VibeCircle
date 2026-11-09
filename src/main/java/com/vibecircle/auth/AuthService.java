package com.vibecircle.auth;

import com.vibecircle.auth.dto.*;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.TokenUtil;
import com.vibecircle.config.AuthProperties;
import com.vibecircle.user.User;
import com.vibecircle.user.UserRepository;
import com.vibecircle.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration VERIFY_TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthProperties props;
    /** Compared against when the user does not exist, so timing doesn't reveal valid accounts. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       VerificationTokenRepository verificationTokens, PasswordEncoder encoder,
                       JwtService jwt, AuthProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    // ------------------------------------------------------------ register
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int age = Period.between(req.dateOfBirth(), today).getYears();
        if (age < props.minAge()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "You must be at least " + props.minAge() + " years old to join VibeCircle");
        }
        if (age > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date of birth");
        }

        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();
        if (users.usernameTaken(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (users.emailTaken(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setDateOfBirth(req.dateOfBirth());
        user.setStatus(props.requireEmailVerification() ? UserStatus.PENDING_VERIFICATION : UserStatus.ACTIVE);

        try {
            users.saveAndFlush(user);   // unique indexes are the real guard against races
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "Username or email is already in use");
        }

        String message = "Account created";
        if (props.requireEmailVerification()) {
            String raw = TokenUtil.newToken();
            verificationTokens.save(new VerificationToken(user.getId(), VerificationType.EMAIL_VERIFY,
                    TokenUtil.sha256Hex(raw), Instant.now().plus(VERIFY_TTL)));
            // TODO: replace with a real email sender. NEVER log tokens in production.
            log.info("DEV ONLY - email verification token for {}: {}", username, raw);
            message = "Account created. Please verify your email.";
        }
        return new RegisterResponse(user.getId(), user.getUsername(), message);
    }

    // --------------------------------------------------------------- login
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String id = req.identifier().trim();
        Optional<User> found = id.contains("@") ? users.findByEmail(id) : users.findByUsername(id);

        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = encoder.matches(req.password(), hash);
        if (found.isEmpty() || !passwordOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        User user = found.get();
        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw new ApiException(HttpStatus.FORBIDDEN, "Please verify your email first");
            case SUSPENDED -> throw new ApiException(HttpStatus.FORBIDDEN, "This account is suspended");
            case DELETED -> throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            case ACTIVE -> { }
        }

        user.setLastSeenAt(Instant.now());
        return issueTokens(user);
    }

    // ------------------------------------------------------------- refresh
    /** noRollbackFor: the "revoke everything" on token reuse must be committed even though we throw. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest req) {
        Instant now = Instant.now();
        RefreshToken old = refreshTokens
                .findByTokenHashForUpdate(TokenUtil.sha256Hex(req.refreshToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (old.getRevokedAt() != null) {
            // A rotated/revoked token came back: assume theft, kill every session for this user.
            refreshTokens.revokeAllForUser(old.getUserId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (old.getExpiresAt().isBefore(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        User user = users.findById(old.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        old.setRevokedAt(now);
        AuthResponse response = issueTokens(user, old);
        return response;
    }

    // -------------------------------------------------------------- logout
    @Transactional
    public void logout(RefreshRequest req) {
        refreshTokens.findByTokenHashForUpdate(TokenUtil.sha256Hex(req.refreshToken()))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
        // Always succeeds: logging out twice, or with a junk token, is not an error.
    }

    // ------------------------------------------------------- verify email
    @Transactional
    public void verifyEmail(VerifyEmailRequest req) {
        Instant now = Instant.now();
        VerificationToken token = verificationTokens
                .findByTokenHashAndType(TokenUtil.sha256Hex(req.token()), VerificationType.EMAIL_VERIFY)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setEmailVerifiedAt(now);
        token.setUsedAt(now);
    }

    // ------------------------------------------------------------ helpers
    private AuthResponse issueTokens(User user) {
        return issueTokens(user, null);
    }

    private AuthResponse issueTokens(User user, RefreshToken replaced) {
        String rawRefresh = TokenUtil.newToken();
        RefreshToken fresh = refreshTokens.saveAndFlush(new RefreshToken(
                user.getId(), TokenUtil.sha256Hex(rawRefresh), Instant.now().plus(props.refreshTokenTtl())));
        if (replaced != null) {
            replaced.setReplacedBy(fresh.getId());
        }
        return new AuthResponse(
                jwt.createAccessToken(user), rawRefresh, "Bearer", jwt.accessTtlSeconds(),
                new AuthResponse.UserSummary(user.getId(), user.getUsername(), user.getFullName(), user.getRole()));
    }
}
