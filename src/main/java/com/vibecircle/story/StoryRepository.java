package com.vibecircle.story;

import com.vibecircle.post.Audience;
import com.vibecircle.post.dto.AuthorDto;
import com.vibecircle.story.dto.StoryView;
import com.vibecircle.story.dto.TrayItem;
import com.vibecircle.story.dto.ViewerView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL. VISIBLE must match AccessPolicyService.canViewStory:
 * author, or friend (any audience), or (PUBLIC story AND PUBLIC author profile); never when blocked.
 * %s placeholders are only filled with the constants below.
 */
@Repository
public class StoryRepository {

    public record RemovableStory(Long id, String storageKey) {
    }

    private static final String IS_FRIEND = """
            EXISTS (SELECT 1 FROM friendships f
                    WHERE f.user_low_id = LEAST(:me, s.author_id) AND f.user_high_id = GREATEST(:me, s.author_id))""";
    private static final String PUBLIC_REACH =
            "(s.audience = 'PUBLIC' AND COALESCE(pr.profile_visibility, 'PUBLIC') = 'PUBLIC')";
    private static final String VISIBLE = "(s.author_id = :me OR " + IS_FRIEND + " OR " + PUBLIC_REACH + ")";

    static final String BY_ID = "AND s.id = :id AND " + VISIBLE;
    static final String BY_AUTHOR = "AND s.author_id = :author AND " + VISIBLE;
    static final String MINE = "AND s.author_id = :me";

    private static final String STORY_SELECT = """
            SELECT s.id, s.author_id, u.username, u.full_name, s.text_overlay, s.media_type,
                   (s.storage_key IS NOT NULL) AS has_media, s.audience, s.created_at, s.expires_at,
                   EXISTS (SELECT 1 FROM story_views v WHERE v.story_id = s.id AND v.viewer_id = :me) AS viewed_by_me,
                   (SELECT count(*) FROM story_views v WHERE v.story_id = s.id) AS view_count
            FROM stories s
            JOIN users u ON u.id = s.author_id
            LEFT JOIN user_profiles pr ON pr.user_id = s.author_id
            WHERE s.deleted_at IS NULL AND s.expires_at > now()
              AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = s.author_id)
                                 OR (b.blocker_id = s.author_id AND b.blocked_id = :me))
              %s
            ORDER BY s.created_at, s.id
            """;

    /** The tray shows me and my friends only: strangers' public stories are reached through their profile. */
    private static final String TRAY = """
            SELECT u.id, u.username, u.full_name,
                   count(*) AS story_count,
                   max(s.created_at) AS latest_at,
                   bool_or(s.author_id <> :me AND NOT EXISTS (
                           SELECT 1 FROM story_views v WHERE v.story_id = s.id AND v.viewer_id = :me)) AS has_unseen
            FROM stories s
            JOIN users u ON u.id = s.author_id
            WHERE s.deleted_at IS NULL AND s.expires_at > now()
              AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND (s.author_id = :me OR EXISTS (SELECT 1 FROM friendships f
                       WHERE f.user_low_id = LEAST(:me, s.author_id) AND f.user_high_id = GREATEST(:me, s.author_id)))
              AND NOT EXISTS (SELECT 1 FROM blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = s.author_id)
                                 OR (b.blocker_id = s.author_id AND b.blocked_id = :me))
            GROUP BY u.id, u.username, u.full_name
            ORDER BY (u.id = :me) DESC, has_unseen DESC, latest_at DESC, u.id
            LIMIT :limit OFFSET :offset
            """;

    private final JdbcClient jdbc;

    public StoryRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class).toInstant();
    }

    // ------------------------------------------------------------------ writes
    /** expires_at is computed by the DATABASE clock, the same clock every visibility check uses. */
    public Long insert(Long authorId, String storageKey, String mediaType, String text, Audience audience, int ttlHours) {
        return jdbc.sql("""
                        INSERT INTO stories (author_id, storage_key, media_type, text_overlay, audience, expires_at)
                        VALUES (:author, :key, :type, :text, :audience, now() + make_interval(hours => :hours))
                        RETURNING id
                        """)
                .param("author", authorId).param("key", storageKey).param("type", mediaType)
                .param("text", text).param("audience", audience.name()).param("hours", ttlHours)
                .query(Long.class).single();
    }

    public boolean softDelete(Long storyId, Long authorId) {
        return jdbc.sql("""
                        UPDATE stories SET deleted_at = now()
                        WHERE id = :id AND author_id = :author AND deleted_at IS NULL AND expires_at > now()
                        """)
                .param("id", storyId).param("author", authorId).update() > 0;
    }

    /** Idempotent, and the author watching their own story is never counted. */
    public int recordView(Long storyId, Long viewerId) {
        return jdbc.sql("""
                        INSERT INTO story_views (story_id, viewer_id)
                        SELECT s.id, :viewer FROM stories s WHERE s.id = :id AND s.author_id <> :viewer
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", storyId).param("viewer", viewerId).update();
    }

    // ------------------------------------------------------------------- reads
    private static StoryView mapStory(ResultSet rs, Long me) throws SQLException {
        long authorId = rs.getLong("author_id");
        boolean mine = me.equals(authorId);
        long id = rs.getLong("id");
        return new StoryView(id, new AuthorDto(authorId, rs.getString("username"), rs.getString("full_name")),
                rs.getString("text_overlay"), rs.getString("media_type"),
                rs.getBoolean("has_media") ? "/api/stories/" + id + "/media" : null,
                Audience.valueOf(rs.getString("audience")), ts(rs, "created_at"), ts(rs, "expires_at"),
                rs.getBoolean("viewed_by_me"), mine ? rs.getLong("view_count") : null, mine);
    }

    public Optional<StoryView> findOne(Long me, Long storyId) {
        return jdbc.sql(STORY_SELECT.formatted(BY_ID)).param("me", me).param("id", storyId)
                .query((rs, i) -> mapStory(rs, me)).optional();
    }

    public List<StoryView> findByAuthor(Long me, Long authorId) {
        return jdbc.sql(STORY_SELECT.formatted(BY_AUTHOR)).param("me", me).param("author", authorId)
                .query((rs, i) -> mapStory(rs, me)).list();
    }

    public List<StoryView> findMine(Long me) {
        return jdbc.sql(STORY_SELECT.formatted(MINE)).param("me", me)
                .query((rs, i) -> mapStory(rs, me)).list();
    }

    public List<TrayItem> tray(Long me, int limit, int offset) {
        return jdbc.sql(TRAY).param("me", me).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new TrayItem(
                        new AuthorDto(rs.getLong("id"), rs.getString("username"), rs.getString("full_name")),
                        rs.getLong("story_count"), rs.getBoolean("has_unseen"), ts(rs, "latest_at")))
                .list();
    }

    /** Author only: does this active story belong to me? */
    public boolean isActiveAuthor(Long storyId, Long me) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM stories
                                       WHERE id = :id AND author_id = :me AND deleted_at IS NULL AND expires_at > now())
                        """)
                .param("id", storyId).param("me", me).query(Boolean.class).single();
    }

    public List<ViewerView> viewers(Long storyId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.full_name, v.viewed_at
                        FROM story_views v JOIN users u ON u.id = v.viewer_id
                        WHERE v.story_id = :id AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        ORDER BY v.viewed_at DESC, u.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("id", storyId).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new ViewerView(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), ts(rs, "viewed_at")))
                .list();
    }

    public Optional<String> findMediaKey(Long storyId) {
        return jdbc.sql("SELECT storage_key FROM stories WHERE id = :id AND storage_key IS NOT NULL")
                .param("id", storyId).query(String.class).optional();
    }

    // ----------------------------------------------------------------- cleanup
    /**
     * Rows that expired (or were deleted) more than `graceSeconds` ago. SKIP LOCKED lets several
     * server instances run the job at the same time without fighting over the same rows.
     */
    public List<RemovableStory> lockRemovable(double graceSeconds, int limit) {
        return jdbc.sql("""
                        SELECT id, storage_key FROM stories
                        WHERE expires_at < now() - make_interval(secs => :grace)
                           OR (deleted_at IS NOT NULL AND deleted_at < now() - make_interval(secs => :grace))
                        ORDER BY id LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("grace", graceSeconds).param("limit", limit)
                .query((rs, i) -> new RemovableStory(rs.getLong("id"), rs.getString("storage_key")))
                .list();
    }

    public void deleteAll(List<Long> ids) {
        jdbc.sql("DELETE FROM stories WHERE id IN (:ids)").param("ids", ids).update();   // story_views cascade
    }
}
