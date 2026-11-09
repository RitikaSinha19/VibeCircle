package com.vibecircle.privacy;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * THE single place that decides "may viewer X see / do Y to user Z?".
 * Posts, stories, chat and calls will add their methods here (canViewPost, canMessage, ...)
 * so blocks, friendship and visibility are never re-implemented per module.
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
}
