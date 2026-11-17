package com.vibecircle.account;

import com.vibecircle.common.ApiException;
import com.vibecircle.media.MediaStorage;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Change password, delete account, and the purge of long-deleted accounts.
 *
 * Deleting an account is two steps. Immediately: the person is anonymised (name, email, username, birth date,
 * password and bio are wiped), signed out everywhere, removed from friends lists, and their content disappears.
 * After the retention period (default 30 days): every remaining row and every media file is erased.
 */
@Service
public class AccountService {

    private static final int PURGE_BATCH = 50;

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final MediaStorage storage;
    private final AccountProperties props;

    public AccountService(DataSource dataSource, PasswordEncoder encoder, MediaStorage storage, AccountProperties props) {
        this.jdbc = JdbcClient.create(dataSource);
        this.encoder = encoder;
        this.storage = storage;
        this.props = props;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        verifyPassword(userId, currentPassword);
        if (currentPassword.equals(newPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new password must be different");
        }
        jdbc.sql("UPDATE users SET password_hash = :hash, updated_at = now() WHERE id = :id")
                .param("hash", encoder.encode(newPassword)).param("id", userId).update();
        revokeSessions(userId);        // every device must log in again with the new password
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        verifyPassword(userId, password);

        jdbc.sql("""
                        UPDATE users SET status = 'DELETED', deleted_at = now(), updated_at = now(),
                               username = 'deleted_' || id, email = 'deleted_' || id || '@deleted.invalid',
                               full_name = 'Deleted user', password_hash = '!', date_of_birth = DATE '1900-01-01'
                        WHERE id = :id
                        """)
                .param("id", userId).update();
        revokeSessions(userId);
        for (String sql : List.of(
                "UPDATE user_profiles SET bio = NULL, profile_picture_key = NULL, cover_picture_key = NULL, discoverable = FALSE WHERE user_id = :id",
                "UPDATE posts SET deleted_at = now() WHERE author_id = :id AND deleted_at IS NULL",
                "UPDATE stories SET deleted_at = now() WHERE author_id = :id AND deleted_at IS NULL",
                "UPDATE comments SET deleted_at = now() WHERE author_id = :id AND deleted_at IS NULL",
                "DELETE FROM friendships WHERE user_low_id = :id OR user_high_id = :id",
                "DELETE FROM friend_requests WHERE sender_id = :id OR receiver_id = :id",
                "DELETE FROM blocks WHERE blocker_id = :id OR blocked_id = :id",
                "DELETE FROM hidden_recommendations WHERE user_id = :id OR hidden_user_id = :id",
                "DELETE FROM notifications WHERE recipient_id = :id",
                "DELETE FROM verification_tokens WHERE user_id = :id")) {
            jdbc.sql(sql).param("id", userId).update();
        }
    }

    /** Erases one batch of accounts deleted longer ago than the retention period, with all their data and files. */
    @Transactional
    public int purgeBatch() {
        double seconds = props.purgeAfter().toMillis() / 1000.0;
        List<Long> ids = jdbc.sql("""
                        SELECT id FROM users
                        WHERE status = 'DELETED' AND deleted_at < now() - make_interval(secs => :s)
                        ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED
                        """)
                .param("s", seconds).param("limit", PURGE_BATCH).query(Long.class).list();
        if (ids.isEmpty()) {
            return 0;
        }
        List<String> keys = jdbc.sql("""
                        SELECT m.storage_key FROM post_media m JOIN posts p ON p.id = m.post_id WHERE p.author_id IN (:ids)
                        UNION ALL SELECT media_key FROM messages WHERE sender_id IN (:ids) AND media_key IS NOT NULL
                        UNION ALL SELECT storage_key FROM stories WHERE author_id IN (:ids) AND storage_key IS NOT NULL
                        """)
                .param("ids", ids).query(String.class).list();

        jdbc.sql("DELETE FROM users WHERE id IN (:ids)").param("ids", ids).update();   // everything else cascades

        if (!keys.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(storage::delete);
                }
            });
        }
        return ids.size();
    }

    // ----------------------------------------------------------------- helpers
    private void verifyPassword(Long userId, String password) {
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE id = :id AND status <> 'DELETED'")
                .param("id", userId).query(String.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!encoder.matches(password, hash)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Password is incorrect");
        }
    }

    private void revokeSessions(Long userId) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :id AND revoked_at IS NULL")
                .param("id", userId).update();
    }
}
