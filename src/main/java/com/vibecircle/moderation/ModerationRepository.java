package com.vibecircle.moderation;

import com.vibecircle.moderation.dto.AdminReportView;
import com.vibecircle.moderation.dto.ReportView;
import com.vibecircle.moderation.dto.TargetPreview;
import com.vibecircle.post.dto.AuthorDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL. %s placeholders are only filled with constant fragments from this class. */
@Repository
public class ModerationRepository {

    /** Who owns the reported item, plus where it lives (used for visibility checks). */
    public record TargetRef(Long ownerId, Long postId, Long conversationId) {
    }

    public record ReportRow(Long id, ReportTargetType targetType, Long targetId, ReportStatus status) {
    }

    public record MessageRef(Long conversationId, Long senderId, String mediaKey) {
    }

    private static final String ADMIN_SELECT = """
            SELECT r.id, r.target_type, r.target_id, r.reason, r.status, r.details, r.resolution,
                   r.created_at, r.reviewed_at,
                   ru.id AS reporter_id, ru.username AS reporter_username, ru.full_name AS reporter_name,
                   rv.username AS reviewer_username,
                   (SELECT count(*) FROM reports x
                     WHERE x.target_type = r.target_type AND x.target_id = r.target_id
                       AND x.status IN ('OPEN', 'UNDER_REVIEW')) AS open_on_target
            FROM reports r
            JOIN users ru ON ru.id = r.reporter_id
            LEFT JOIN users rv ON rv.id = r.reviewed_by
            WHERE %s
            """;

    private final JdbcClient jdbc;

    public ModerationRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    // ----------------------------------------------------- reporting (user side)
    /** The item exists and is not already removed. Empty = nothing to report. */
    public Optional<TargetRef> findActiveTarget(ReportTargetType type, Long id) {
        return switch (type) {
            case USER -> jdbc.sql("SELECT id FROM users WHERE id = :id AND deleted_at IS NULL AND status <> 'DELETED'")
                    .param("id", id).query(Long.class).optional()
                    .map(owner -> new TargetRef(owner, null, null));
            case POST -> jdbc.sql("SELECT author_id FROM posts WHERE id = :id AND deleted_at IS NULL")
                    .param("id", id).query(Long.class).optional()
                    .map(owner -> new TargetRef(owner, id, null));
            case COMMENT -> jdbc.sql("SELECT author_id, post_id FROM comments WHERE id = :id AND deleted_at IS NULL")
                    .param("id", id)
                    .query((rs, i) -> new TargetRef(rs.getLong("author_id"), rs.getLong("post_id"), null))
                    .optional();
            case MESSAGE -> jdbc.sql("SELECT sender_id, conversation_id FROM messages WHERE id = :id AND unsent_at IS NULL")
                    .param("id", id)
                    .query((rs, i) -> new TargetRef(rs.getLong("sender_id"), null, rs.getLong("conversation_id")))
                    .optional();
        };
    }

    public long countRecentByReporter(Long reporterId) {
        return jdbc.sql("SELECT count(*) FROM reports WHERE reporter_id = :me AND created_at > now() - interval '1 hour'")
                .param("me", reporterId).query(Long.class).single();
    }

    public Long insertReport(Long reporterId, ReportTargetType type, Long targetId, ReportReason reason, String details) {
        return jdbc.sql("""
                        INSERT INTO reports (reporter_id, target_type, target_id, reason, details)
                        VALUES (:reporter, :type, :target, :reason, :details) RETURNING id
                        """)
                .param("reporter", reporterId).param("type", type.name()).param("target", targetId)
                .param("reason", reason.name()).param("details", details)
                .query(Long.class).single();
    }

    private static ReportView mapMine(ResultSet rs, int i) throws SQLException {
        return new ReportView(rs.getLong("id"), ReportTargetType.valueOf(rs.getString("target_type")),
                rs.getLong("target_id"), ReportReason.valueOf(rs.getString("reason")),
                ReportStatus.valueOf(rs.getString("status")), ts(rs, "created_at"));
    }

    public Optional<ReportView> findMine(Long reportId, Long reporterId) {
        return jdbc.sql("""
                        SELECT id, target_type, target_id, reason, status, created_at
                        FROM reports WHERE id = :id AND reporter_id = :me
                        """)
                .param("id", reportId).param("me", reporterId)
                .query(ModerationRepository::mapMine).optional();
    }

    public List<ReportView> listMine(Long reporterId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, target_type, target_id, reason, status, created_at
                        FROM reports WHERE reporter_id = :me
                        ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
                        """)
                .param("me", reporterId).param("limit", limit).param("offset", offset)
                .query(ModerationRepository::mapMine).list();
    }

    // ------------------------------------------------------------ review queue
    private static AdminReportView mapAdmin(ResultSet rs, int i) throws SQLException {
        return new AdminReportView(rs.getLong("id"), ReportTargetType.valueOf(rs.getString("target_type")),
                rs.getLong("target_id"), ReportReason.valueOf(rs.getString("reason")),
                ReportStatus.valueOf(rs.getString("status")), rs.getString("details"),
                new AuthorDto(rs.getLong("reporter_id"), rs.getString("reporter_username"), rs.getString("reporter_name")),
                rs.getLong("open_on_target"), rs.getString("reviewer_username"), rs.getString("resolution"),
                ts(rs, "created_at"), ts(rs, "reviewed_at"), null);
    }

    /** Busiest targets first (most open reports), then oldest first. */
    public List<AdminReportView> listAdmin(ReportStatus status, ReportTargetType type, int limit, int offset) {
        String filter = "r.status = :status" + (type != null ? " AND r.target_type = :type" : "");
        JdbcClient.StatementSpec spec = jdbc
                .sql(ADMIN_SELECT.formatted(filter) + " ORDER BY open_on_target DESC, r.created_at, r.id LIMIT :limit OFFSET :offset")
                .param("status", status.name()).param("limit", limit).param("offset", offset);
        if (type != null) {
            spec = spec.param("type", type.name());
        }
        return spec.query(ModerationRepository::mapAdmin).list();
    }

    public Optional<AdminReportView> findAdmin(Long reportId) {
        return jdbc.sql(ADMIN_SELECT.formatted("r.id = :id")).param("id", reportId)
                .query(ModerationRepository::mapAdmin).optional();
    }

    public Optional<ReportRow> lockReport(Long reportId) {
        return jdbc.sql("SELECT id, target_type, target_id, status FROM reports WHERE id = :id FOR UPDATE")
                .param("id", reportId)
                .query((rs, i) -> new ReportRow(rs.getLong("id"), ReportTargetType.valueOf(rs.getString("target_type")),
                        rs.getLong("target_id"), ReportStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    /** OPEN -> UNDER_REVIEW. Empty = missing or no longer OPEN. */
    public Optional<Long> claim(Long reportId, Long moderatorId) {
        return jdbc.sql("""
                        UPDATE reports SET status = 'UNDER_REVIEW', reviewed_by = :mod
                        WHERE id = :id AND status = 'OPEN' RETURNING id
                        """)
                .param("id", reportId).param("mod", moderatorId)
                .query(Long.class).optional();
    }

    /** Closes EVERY open report about the same item, so the queue does not fill with duplicates. */
    public int resolveAllForTarget(ReportTargetType type, Long targetId, ReportStatus status,
                                   Long moderatorId, String resolution) {
        return jdbc.sql("""
                        UPDATE reports SET status = :status, reviewed_by = :mod, resolution = :resolution,
                                           reviewed_at = now()
                        WHERE target_type = :type AND target_id = :target AND status IN ('OPEN', 'UNDER_REVIEW')
                        """)
                .param("status", status.name()).param("mod", moderatorId).param("resolution", resolution)
                .param("type", type.name()).param("target", targetId)
                .update();
    }

    public void logAction(Long moderatorId, String action, ReportTargetType type, Long targetId,
                          Long reportId, String note) {
        jdbc.sql("""
                        INSERT INTO moderation_actions (moderator_id, action, target_type, target_id, report_id, note)
                        VALUES (:mod, :action, :type, :target, :report, :note)
                        """)
                .param("mod", moderatorId).param("action", action).param("type", type.name())
                .param("target", targetId).param("report", reportId).param("note", note)
                .update();
    }

    // ------------------------------------------------------- target snapshots
    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /** Works even for items that were already removed (the reviewer still sees what was reported). */
    public Optional<TargetPreview> preview(ReportTargetType type, Long id) {
        return switch (type) {
            case USER -> jdbc.sql("SELECT id, username, full_name, status FROM users WHERE id = :id")
                    .param("id", id)
                    .query((rs, i) -> new TargetPreview("USER", id, rs.getLong("id"), rs.getString("username"),
                            rs.getString("status"), rs.getString("full_name"), "DELETED".equals(rs.getString("status"))))
                    .optional();
            case POST -> jdbc.sql("""
                            SELECT p.author_id, u.username, u.status, p.caption, p.deleted_at,
                                   (SELECT count(*) FROM post_media m WHERE m.post_id = p.id) AS media
                            FROM posts p JOIN users u ON u.id = p.author_id WHERE p.id = :id
                            """)
                    .param("id", id)
                    .query((rs, i) -> new TargetPreview("POST", id, rs.getLong("author_id"), rs.getString("username"),
                            rs.getString("status"),
                            clip(rs.getString("caption"), 300) + (rs.getLong("media") > 0 ? " [+" + rs.getLong("media") + " media]" : ""),
                            rs.getObject("deleted_at") != null))
                    .optional();
            case COMMENT -> jdbc.sql("""
                            SELECT c.author_id, u.username, u.status, c.body, c.deleted_at
                            FROM comments c JOIN users u ON u.id = c.author_id WHERE c.id = :id
                            """)
                    .param("id", id)
                    .query((rs, i) -> new TargetPreview("COMMENT", id, rs.getLong("author_id"), rs.getString("username"),
                            rs.getString("status"), clip(rs.getString("body"), 300), rs.getObject("deleted_at") != null))
                    .optional();
            case MESSAGE -> jdbc.sql("""
                            SELECT m.sender_id, u.username, u.status, m.type, m.body, m.unsent_at
                            FROM messages m JOIN users u ON u.id = m.sender_id WHERE m.id = :id
                            """)
                    .param("id", id)
                    .query((rs, i) -> new TargetPreview("MESSAGE", id, rs.getLong("sender_id"), rs.getString("username"),
                            rs.getString("status"),
                            "IMAGE".equals(rs.getString("type")) ? "[image]" : clip(rs.getString("body"), 300),
                            rs.getObject("unsent_at") != null))
                    .optional();
        };
    }

    // ----------------------------------------------------------- enforcement
    public void removePost(Long postId) {
        jdbc.sql("UPDATE posts SET deleted_at = now(), updated_at = now() WHERE id = :id AND deleted_at IS NULL")
                .param("id", postId).update();
    }

    public void removeComment(Long commentId) {
        jdbc.sql("UPDATE comments SET deleted_at = now() WHERE id = :id AND deleted_at IS NULL")
                .param("id", commentId).update();
    }

    public Optional<MessageRef> lockMessageForRemoval(Long messageId) {
        return jdbc.sql("""
                        SELECT conversation_id, sender_id, media_key FROM messages
                        WHERE id = :id AND unsent_at IS NULL FOR UPDATE
                        """)
                .param("id", messageId)
                .query((rs, i) -> new MessageRef(rs.getLong("conversation_id"), rs.getLong("sender_id"),
                        rs.getString("media_key")))
                .optional();
    }

    /** Same tombstone the sender's own "unsend" produces. */
    public void removeMessage(Long messageId) {
        jdbc.sql("UPDATE messages SET unsent_at = now(), body = '', media_key = NULL WHERE id = :id")
                .param("id", messageId).update();
    }

    public Optional<Long> otherParticipant(Long conversationId, Long userId) {
        return jdbc.sql("SELECT user_id FROM conversation_members WHERE conversation_id = :c AND user_id <> :u")
                .param("c", conversationId).param("u", userId).query(Long.class).optional();
    }

    public Optional<String> userRole(Long userId) {
        return jdbc.sql("SELECT role FROM users WHERE id = :id").param("id", userId).query(String.class).optional();
    }

    public void suspendUser(Long userId) {
        jdbc.sql("UPDATE users SET status = 'SUSPENDED', updated_at = now() WHERE id = :id AND status = 'ACTIVE'")
                .param("id", userId).update();
        // end every session: the refresh tokens stop working immediately
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :id AND revoked_at IS NULL")
                .param("id", userId).update();
    }

    public boolean unsuspendUser(Long userId) {
        return jdbc.sql("UPDATE users SET status = 'ACTIVE', updated_at = now() WHERE id = :id AND status = 'SUSPENDED'")
                .param("id", userId).update() > 0;
    }
}
