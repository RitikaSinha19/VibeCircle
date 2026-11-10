package com.vibecircle.post;

import com.vibecircle.post.dto.AuthorDto;
import com.vibecircle.post.dto.MediaView;
import com.vibecircle.post.dto.PostView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL. The SCOPE_* fragments encode the visibility rules in SQL for list queries and
 * MUST match AccessPolicyService.canViewPost:
 *   author, or friend of the author (any audience), or (PUBLIC post AND PUBLIC author profile);
 *   never when blocked either way. %s placeholders are only filled with the constants below.
 */
@Repository
public class PostRepository {

    private static final String IS_FRIEND = """
            EXISTS (SELECT 1 FROM friendships f
                    WHERE f.user_low_id = LEAST(:me, p.author_id) AND f.user_high_id = GREATEST(:me, p.author_id))""";
    private static final String PUBLIC_REACH =
            "(p.audience = 'PUBLIC' AND COALESCE(pr.profile_visibility, 'PUBLIC') = 'PUBLIC')";
    private static final String VISIBLE = "(p.author_id = :me OR " + IS_FRIEND + " OR " + PUBLIC_REACH + ")";

    static final String SCOPE_ALL = "AND " + VISIBLE;
    static final String SCOPE_FRIENDS = "AND (p.author_id = :me OR " + IS_FRIEND + ")";
    static final String SCOPE_AUTHOR = "AND p.author_id = :author AND " + VISIBLE;
    static final String SCOPE_SINGLE = "AND p.id = :postId AND " + VISIBLE;

    private static final String BASE = """
            SELECT p.id, p.author_id, u.username, u.full_name, p.caption, p.audience, p.created_at,
                   c.id AS city_id, c.name AS city_name,
                   (SELECT count(*) FROM post_likes l WHERE l.post_id = p.id) AS like_count,
                   (SELECT count(*) FROM comments cm WHERE cm.post_id = p.id AND cm.deleted_at IS NULL) AS comment_count,
                   EXISTS (SELECT 1 FROM post_likes l WHERE l.post_id = p.id AND l.user_id = :me) AS liked_by_me
            FROM posts p
            JOIN users u ON u.id = p.author_id
            LEFT JOIN user_profiles pr ON pr.user_id = p.author_id
            LEFT JOIN cities c ON c.id = p.city_id
            WHERE p.deleted_at IS NULL
              AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = p.author_id)
                                 OR (b.blocker_id = p.author_id AND b.blocked_id = :me))
              %s
              %s
            ORDER BY p.id DESC
            LIMIT :limit
            """;

    private record Row(long id, long authorId, String username, String fullName, String caption, String audience,
                       Instant createdAt, Long cityId, String cityName, long likeCount, long commentCount,
                       boolean likedByMe) {
    }

    private final JdbcClient jdbc;

    public PostRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    // ----------------------------------------------------------------- reads
    public List<PostView> feed(Long me, boolean friendsOnly, Long beforeId, int limit) {
        return query(me, friendsOnly ? SCOPE_FRIENDS : SCOPE_ALL, null, null, beforeId, limit);
    }

    public List<PostView> byAuthor(Long me, Long authorId, Long beforeId, int limit) {
        return query(me, SCOPE_AUTHOR, authorId, null, beforeId, limit);
    }

    public Optional<PostView> findView(Long me, Long postId) {
        return query(me, SCOPE_SINGLE, null, postId, null, 1).stream().findFirst();
    }

    private List<PostView> query(Long me, String scope, Long author, Long postId, Long beforeId, int limit) {
        String sql = BASE.formatted(scope, beforeId != null ? "AND p.id < :beforeId" : "");
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("me", me).param("limit", limit);
        if (author != null) spec = spec.param("author", author);
        if (postId != null) spec = spec.param("postId", postId);
        if (beforeId != null) spec = spec.param("beforeId", beforeId);
        List<Row> rows = spec.query(PostRepository::mapRow).list();
        return hydrate(me, rows);
    }

    private static Row mapRow(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("author_id"), rs.getString("username"),
                rs.getString("full_name"), rs.getString("caption"), rs.getString("audience"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                (Long) rs.getObject("city_id"), rs.getString("city_name"),
                rs.getLong("like_count"), rs.getLong("comment_count"), rs.getBoolean("liked_by_me"));
    }

    /** Two small extra queries fetch media and hashtags for just this page of posts. */
    private List<PostView> hydrate(Long me, List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Row::id).toList();

        Map<Long, List<MediaView>> media = new HashMap<>();
        jdbc.sql("SELECT id, post_id, media_type FROM post_media WHERE post_id IN (:ids) ORDER BY post_id, position")
                .param("ids", ids)
                .query((rs, i) -> Map.entry(rs.getLong("post_id"),
                        new MediaView(rs.getLong("id"), rs.getString("media_type"), "/api/media/" + rs.getLong("id"))))
                .list()
                .forEach(e -> media.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        Map<Long, List<String>> tags = new HashMap<>();
        jdbc.sql("""
                        SELECT ph.post_id, h.tag FROM post_hashtags ph
                        JOIN hashtags h ON h.id = ph.hashtag_id
                        WHERE ph.post_id IN (:ids) ORDER BY ph.post_id, h.tag
                        """)
                .param("ids", ids)
                .query((rs, i) -> Map.entry(rs.getLong("post_id"), rs.getString("tag")))
                .list()
                .forEach(e -> tags.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        return rows.stream().map(r -> new PostView(
                r.id(), new AuthorDto(r.authorId(), r.username(), r.fullName()), r.caption(),
                Audience.valueOf(r.audience()), r.cityId(), r.cityName(),
                media.getOrDefault(r.id(), List.of()), tags.getOrDefault(r.id(), List.of()),
                r.likeCount(), r.commentCount(), r.likedByMe(), r.authorId() == me, r.createdAt())).toList();
    }

    public boolean cityExists(Long cityId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM cities WHERE id = :id)")
                .param("id", cityId).query(Boolean.class).single();
    }

    public Optional<Long> findAuthorId(Long postId) {
        return jdbc.sql("SELECT author_id FROM posts WHERE id = :id AND deleted_at IS NULL")
                .param("id", postId).query(Long.class).optional();
    }

    public long likeCount(Long postId) {
        return jdbc.sql("SELECT count(*) FROM post_likes WHERE post_id = :id")
                .param("id", postId).query(Long.class).single();
    }

    // ---------------------------------------------------------------- writes
    public Long insertPost(Long authorId, String caption, Audience audience, Long cityId) {
        return jdbc.sql("""
                        INSERT INTO posts (author_id, caption, audience, city_id)
                        VALUES (:author, :caption, :audience, :city) RETURNING id
                        """)
                .param("author", authorId)
                .param("caption", caption)
                .param("audience", audience.name())
                .param("city", cityId)
                .query(Long.class).single();
    }

    public void insertMedia(Long postId, String key, String mediaType, String contentType, long size, int position) {
        jdbc.sql("""
                        INSERT INTO post_media (post_id, storage_key, media_type, content_type, size_bytes, position)
                        VALUES (:post, :key, :type, :ctype, :size, :pos)
                        """)
                .param("post", postId).param("key", key).param("type", mediaType)
                .param("ctype", contentType).param("size", size).param("pos", position)
                .update();
    }

    public void replaceHashtags(Long postId, Set<String> tags) {
        jdbc.sql("DELETE FROM post_hashtags WHERE post_id = :post").param("post", postId).update();
        for (String tag : tags) {
            // no-op DO UPDATE so RETURNING gives us the id whether the tag is new or not
            Long tagId = jdbc.sql("""
                            INSERT INTO hashtags (tag) VALUES (:tag)
                            ON CONFLICT (tag) DO UPDATE SET tag = EXCLUDED.tag RETURNING id
                            """)
                    .param("tag", tag).query(Long.class).single();
            jdbc.sql("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (:post, :tag) ON CONFLICT DO NOTHING")
                    .param("post", postId).param("tag", tagId).update();
        }
    }

    /** Only the author's own, non-deleted post. Returns false if nothing matched. */
    public boolean updatePost(Long postId, Long authorId, boolean setCaption, String caption, Audience audience) {
        StringBuilder set = new StringBuilder("updated_at = now()");
        if (setCaption) set.append(", caption = :caption");
        if (audience != null) set.append(", audience = :audience");
        JdbcClient.StatementSpec spec = jdbc
                .sql("UPDATE posts SET " + set + " WHERE id = :id AND author_id = :author AND deleted_at IS NULL")
                .param("id", postId).param("author", authorId);
        if (setCaption) spec = spec.param("caption", caption);
        if (audience != null) spec = spec.param("audience", audience.name());
        return spec.update() > 0;
    }

    public boolean softDelete(Long postId, Long authorId) {
        return jdbc.sql("""
                        UPDATE posts SET deleted_at = now(), updated_at = now()
                        WHERE id = :id AND author_id = :author AND deleted_at IS NULL
                        """)
                .param("id", postId).param("author", authorId).update() > 0;
    }

    /** returns 1 if a new like was recorded, 0 if it already existed (idempotent) */
    public int like(Long postId, Long userId) {
        return jdbc.sql("INSERT INTO post_likes (post_id, user_id) VALUES (:post, :user) ON CONFLICT DO NOTHING")
                .param("post", postId).param("user", userId).update();
    }

    public int unlike(Long postId, Long userId) {
        return jdbc.sql("DELETE FROM post_likes WHERE post_id = :post AND user_id = :user")
                .param("post", postId).param("user", userId).update();
    }
}
