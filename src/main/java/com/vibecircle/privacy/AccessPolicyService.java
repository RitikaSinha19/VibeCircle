package com.vibecircle.privacy;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * THE single place that decides "may viewer X see / do Y to user Z?".
 * Stories, chat and calls will add their methods here (canViewStory, canMessage, ...)
 * so blocks, friendship and visibility are never re-implemented per module.
 *
 * NOTE: list queries (feed, user posts) must apply the SAME rules in SQL, because they
 * cannot call a per-row method. Keep PostRepository's scope fragments in sync with this class.
 */
@Service
public class AccessPolicyService {

    private final JdbcClient jdbc;

    public AccessPolicyService(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public boolean blockedEitherWay(Long a, Long b) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM blocks
                                       WHERE (blocker_id = :a AND blocked_id = :b)
                                          OR (blocker_id = :b AND blocked_id = :a))
                        """)
                .param("a", a).param("b", b)
                .query(Boolean.class).single();
    }

    public boolean areFriends(Long a, Long b) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM friendships
                                       WHERE user_low_id = LEAST(:a, :b) AND user_high_id = GREATEST(:a, :b))
                        """)
                .param("a", a).param("b", b)
                .query(Boolean.class).single();
    }

    /**
     * What may the viewer see of the owner's profile?
     *   missing / inactive / blocked either way -> NONE
     *   owner is the viewer, a friend, or profile is PUBLIC -> FULL
     *   PRIVATE profile and not a friend -> LIMITED
     */
    public ProfileAccess profileAccess(Long viewerId, Long ownerId) {
        if (viewerId.equals(ownerId)) {
            return ProfileAccess.FULL;
        }
        return jdbc.sql("""
                        SELECT COALESCE(p.profile_visibility, 'PUBLIC') AS visibility,
                               EXISTS (SELECT 1 FROM blocks b
                                       WHERE (b.blocker_id = :viewer AND b.blocked_id = u.id)
                                          OR (b.blocker_id = u.id AND b.blocked_id = :viewer)) AS blocked,
                               EXISTS (SELECT 1 FROM friendships f
                                       WHERE f.user_low_id = LEAST(:viewer, u.id)
                                         AND f.user_high_id = GREATEST(:viewer, u.id)) AS friends
                        FROM users u
                        LEFT JOIN user_profiles p ON p.user_id = u.id
                        WHERE u.id = :owner AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        """)
                .param("viewer", viewerId)
                .param("owner", ownerId)
                .query((rs, i) -> {
                    if (rs.getBoolean("blocked")) return ProfileAccess.NONE;
                    if (rs.getBoolean("friends") || "PUBLIC".equals(rs.getString("visibility"))) {
                        return ProfileAccess.FULL;
                    }
                    return ProfileAccess.LIMITED;
                })
                .optional()
                .orElse(ProfileAccess.NONE);
    }

    /**
     * May the viewer see this post (and therefore its media, likes and comments)?
     *   deleted post / inactive author / blocked either way -> no
     *   the author, or a friend of the author              -> yes (any audience)
     *   anyone else -> only if the post is PUBLIC *and* the author's profile is PUBLIC
     * Callers should answer "no" with 404, never 403, so private posts don't reveal they exist.
     */
    public boolean canViewPost(Long viewerId, Long postId) {
        return jdbc.sql("""
                        SELECT p.author_id, p.audience,
                               COALESCE(pr.profile_visibility, 'PUBLIC') AS visibility,
                               EXISTS (SELECT 1 FROM blocks b
                                       WHERE (b.blocker_id = :viewer AND b.blocked_id = p.author_id)
                                          OR (b.blocker_id = p.author_id AND b.blocked_id = :viewer)) AS blocked,
                               EXISTS (SELECT 1 FROM friendships f
                                       WHERE f.user_low_id = LEAST(:viewer, p.author_id)
                                         AND f.user_high_id = GREATEST(:viewer, p.author_id)) AS friends
                        FROM posts p
                        JOIN users u ON u.id = p.author_id
                        LEFT JOIN user_profiles pr ON pr.user_id = p.author_id
                        WHERE p.id = :post AND p.deleted_at IS NULL
                          AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        """)
                .param("viewer", viewerId)
                .param("post", postId)
                .query((rs, i) -> {
                    if (viewerId.equals(rs.getLong("author_id"))) return true;
                    if (rs.getBoolean("blocked")) return false;
                    if (rs.getBoolean("friends")) return true;
                    return "PUBLIC".equals(rs.getString("audience")) && "PUBLIC".equals(rs.getString("visibility"));
                })
                .optional()
                .orElse(false);
    }

    /**
     * May these two users exchange chat messages / typing events?
     * Yes only if both accounts are active, they are friends, and neither has blocked the other.
     * (Blocking already deletes the friendship; the explicit block check is defense in depth.)
     */
    public boolean canMessage(Long a, Long b) {
        if (a.equals(b)) {
            return false;
        }
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM friendships f
                                       JOIN users ua ON ua.id = :a
                                       JOIN users ub ON ub.id = :b
                                       WHERE f.user_low_id = LEAST(:a, :b) AND f.user_high_id = GREATEST(:a, :b)
                                         AND ua.status = 'ACTIVE' AND ua.deleted_at IS NULL
                                         AND ub.status = 'ACTIVE' AND ub.deleted_at IS NULL)
                           AND NOT EXISTS (SELECT 1 FROM blocks b
                                           WHERE (b.blocker_id = :a AND b.blocked_id = :b)
                                              OR (b.blocker_id = :b AND b.blocked_id = :a))
                        """)
                .param("a", a).param("b", b)
                .query(Boolean.class).single();
    }

    /**
     * May the viewer see this story (and therefore its media and its view tracking)?
     *   deleted / expired story, inactive author, blocked either way -> no
     *   the author, or a friend of the author                         -> yes (any audience)
     *   anyone else -> only if the story is PUBLIC and the author's profile is PUBLIC
     * Callers should answer "no" with 404.
     */
    public boolean canViewStory(Long viewerId, Long storyId) {
        return jdbc.sql("""
                        SELECT s.author_id, s.audience,
                               COALESCE(pr.profile_visibility, 'PUBLIC') AS visibility,
                               EXISTS (SELECT 1 FROM blocks b
                                       WHERE (b.blocker_id = :viewer AND b.blocked_id = s.author_id)
                                          OR (b.blocker_id = s.author_id AND b.blocked_id = :viewer)) AS blocked,
                               EXISTS (SELECT 1 FROM friendships f
                                       WHERE f.user_low_id = LEAST(:viewer, s.author_id)
                                         AND f.user_high_id = GREATEST(:viewer, s.author_id)) AS friends
                        FROM stories s
                        JOIN users u ON u.id = s.author_id
                        LEFT JOIN user_profiles pr ON pr.user_id = s.author_id
                        WHERE s.id = :story AND s.deleted_at IS NULL AND s.expires_at > now()
                          AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        """)
                .param("viewer", viewerId)
                .param("story", storyId)
                .query((rs, i) -> {
                    if (viewerId.equals(rs.getLong("author_id"))) return true;
                    if (rs.getBoolean("blocked")) return false;
                    if (rs.getBoolean("friends")) return true;
                    return "PUBLIC".equals(rs.getString("audience")) && "PUBLIC".equals(rs.getString("visibility"));
                })
                .optional()
                .orElse(false);
    }
}
