package com.vibecircle.account;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.TokenUtil;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Forgot / reset password. Reset tokens are random, stored only as a hash, valid for one hour and usable once;
 * asking for a new one invalidates the old ones. The "forgot" answer never reveals whether an account exists.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final String baseUrl;

    public PasswordResetService(DataSource dataSource, PasswordEncoder encoder, ObjectProvider<EmailService> emails,
                                Environment env, @Value("${vibecircle.app.base-url:http://localhost:5173}") String baseUrl) {
        this.jdbc = JdbcClient.create(dataSource);
        this.encoder = encoder;
        boolean production = env.acceptsProfiles(Profiles.of("prod"));
        this.email = emails.getIfAvailable(() -> new LoggingEmailService(!production));
        this.baseUrl = baseUrl;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String address = rawEmail.trim();
        Optional<Long> userId = jdbc.sql("""
                        SELECT id FROM users
                        WHERE lower(email) = lower(:email) AND status = 'ACTIVE' AND deleted_at IS NULL
                        """)
                .param("email", address).query(Long.class).optional();
        if (userId.isEmpty()) {
            return;                                              // same answer as for a real account
        }
        Long id = userId.get();
        jdbc.sql("UPDATE verification_tokens SET used_at = now() WHERE user_id = :id AND type = 'PASSWORD_RESET' AND used_at IS NULL")
                .param("id", id).update();
        String token = TokenUtil.newToken();
        jdbc.sql("""
                        INSERT INTO verification_tokens (user_id, type, token_hash, expires_at)
                        VALUES (:id, 'PASSWORD_RESET', :hash, now() + interval '1 hour')
                        """)
                .param("id", id).param("hash", TokenUtil.sha256Hex(token)).update();

        String link = baseUrl + "/reset-password?token=" + token;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    email.send(address, "Reset your VibeCircle password",
                            "Use this link within one hour to choose a new password:\n" + link
                                    + "\n\nIf you did not ask for this, ignore this email.");
                } catch (RuntimeException e) {
                    // the answer to the caller must not depend on whether the mail provider is up
                    log.warn("Could not send the password-reset email", e);
                }
            }
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        // one atomic statement: of two simultaneous uses of the same token, exactly one wins
        Long userId = jdbc.sql("""
                        UPDATE verification_tokens SET used_at = now()
                        WHERE token_hash = :hash AND type = 'PASSWORD_RESET' AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id
                        """)
                .param("hash", TokenUtil.sha256Hex(token)).query(Long.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        int changed = jdbc.sql("""
                        UPDATE users SET password_hash = :hash, updated_at = now()
                        WHERE id = :id AND status <> 'DELETED' AND deleted_at IS NULL
                        """)
                .param("hash", encoder.encode(newPassword)).param("id", userId).update();
        if (changed == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        // everyone who knew the old password is logged out
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :id AND revoked_at IS NULL")
                .param("id", userId).update();
    }
}
