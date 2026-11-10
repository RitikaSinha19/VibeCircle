
package com.vibecircle.notification;
 
import com.vibecircle.notification.dto.NotificationView;
import com.vibecircle.post.dto.AuthorDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
 
@Repository
public class NotificationRepository {
 
    private static final String SELECT = """
            SELECT n.id, n.type, n.entity_type, n.entity_id, n.read_at, n.created_at,
                   a.id AS actor_id, a.username AS actor_username, a.full_name AS actor_name
            FROM notifications n
            LEFT JOIN users a ON a.id = n.actor_id
            WHERE %s
            """;
 
    /** Repeated likes from the same person on the same post notify only once. */
    private static final String DEDUP = """
            AND NOT EXISTS (SELECT 1 FROM notifications x
                            WHERE x.recipient_id = :recipient AND x.actor_id = :actor AND x.type = :type
                              AND x.entity_type = :entityType AND x.entity_id = :entityId)""";
 
    private final JdbcClient jdbc;
 
    public NotificationRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }
 
    private static NotificationView map(ResultSet rs, int i) throws SQLException {
        NotificationType type = NotificationType.valueOf(rs.getString("type"));
        Long actorId = (Long) rs.getObject("actor_id");
        AuthorDto actor = actorId == null ? null
                : new AuthorDto(actorId, rs.getString("actor_username"), rs.getString("actor_name"));
        return new NotificationView(rs.getLong("id"), type, message(type, actor), actor,
                rs.getString("entity_type"), (Long) rs.getObject("entity_id"),
                rs.getObject("read_at") != null,
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
 
    private static String message(NotificationType type, AuthorDto actor) {
        String who = actor == null ? "Someone" : actor.username();
        return switch (type) {
            case FRIEND_REQUEST -> who + " sent you a friend request";
            case FRIEND_ACCEPTED -> who + " accepted your friend request";
            case POST_LIKED -> who + " liked your post";
            case NEW_COMMENT -> who + " commented on your post";
            case NEW_MESSAGE -> who + " sent you a message";
            case INCOMING_CALL -> who + " is calling you";
            case MISSED_CALL -> "You missed a call from " + who;
            case NEW_STORY -> who + " posted a new story";
        };
    }
 
    /**
     * Inserts unless: blocked either way, the recipient switched this type off, or (likes) it is a duplicate.
     * Empty result = intentionally skipped.
     */
    public Optional<Long> insert(Long recipient, Long actor, NotificationType type, String entityType, Long entityId) {
        String sql = """
                INSERT INTO notifications (recipient_id, actor_id, type, entity_type, entity_id)
                SELECT :recipient, :actor, :type, :entityType, :entityId
                WHERE NOT EXISTS (SELECT 1 FROM blocks b
                                  WHERE (b.blocker_id = :recipient AND b.blocked_id = :actor)
                                     OR (b.blocker_id = :actor AND b.blocked_id = :recipient))
                  AND NOT EXISTS (SELECT 1 FROM notification_preferences np
                                  WHERE np.user_id = :recipient AND np.type = :type AND NOT np.enabled)
                  %s
                RETURNING id
                """.formatted(type == NotificationType.POST_LIKED ? DEDUP : "");
        return jdbc.sql(sql)
                .param("recipient", recipient).param("actor", actor)
                .param("type", type.name()).param("entityType", entityType).param("entityId", entityId)
                .query(Long.class).optional();
    }
 
    public Optional<NotificationView> findView(Long id) {
        return jdbc.sql(SELECT.formatted("n.id = :id")).param("id", id)
                .query(NotificationRepository::map).optional();
    }
 
    public List<NotificationView> list(Long me, boolean unreadOnly, int limit, int offset) {
        String where = "n.recipient_id = :me" + (unreadOnly ? " AND n.read_at IS NULL" : "");
        return jdbc.sql(SELECT.formatted(where) + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit OFFSET :offset")
                .param("me", me).param("limit", limit).param("offset", offset)
                .query(NotificationRepository::map).list();
    }
 
    public long unreadCount(Long me) {
        return jdbc.sql("SELECT count(*) FROM notifications WHERE recipient_id = :me AND read_at IS NULL")
                .param("me", me).query(Long.class).single();
    }
 
    /** Idempotent: marking an already-read notification is fine. False = not found or not yours. */
    public boolean markRead(Long id, Long me) {
        return jdbc.sql("""
                        UPDATE notifications SET read_at = COALESCE(read_at, now())
                        WHERE id = :id AND recipient_id = :me
                        """)
                .param("id", id).param("me", me).update() > 0;
    }
 
    public int markAllRead(Long me) {
        return jdbc.sql("UPDATE notifications SET read_at = now() WHERE recipient_id = :me AND read_at IS NULL")
                .param("me", me).update();
    }
 
    public boolean delete(Long id, Long me) {
        return jdbc.sql("DELETE FROM notifications WHERE id = :id AND recipient_id = :me")
                .param("id", id).param("me", me).update() > 0;
    }
 
    // -------------------------------------------------------------- preferences
    public Set<NotificationType> disabledTypes(Long me) {
        Set<NotificationType> disabled = new HashSet<>();
        jdbc.sql("SELECT type FROM notification_preferences WHERE user_id = :me AND NOT enabled")
                .param("me", me).query(String.class).list()
                .forEach(t -> disabled.add(NotificationType.valueOf(t)));
        return disabled;
    }
 
    public void setPreference(Long me, NotificationType type, boolean enabled) {
        jdbc.sql("""
                        INSERT INTO notification_preferences (user_id, type, enabled) VALUES (:me, :type, :enabled)
                        ON CONFLICT (user_id, type) DO UPDATE SET enabled = EXCLUDED.enabled
                        """)
                .param("me", me).param("type", type.name()).param("enabled", enabled).update();
    }
}
 

