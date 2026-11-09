package com.vibecircle.friendship;

import com.vibecircle.friendship.dto.BlockedUserDto;
import com.vibecircle.friendship.dto.FriendDto;
import com.vibecircle.friendship.dto.FriendRequestView;
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
 * Plain SQL on purpose: every state change is ONE atomic statement
 * (UPDATE ... WHERE status = 'PENDING' RETURNING ...), so two simultaneous accepts or
 * cancels cannot both win, and the unique indexes from V1 stop duplicates.
 * The %s placeholders below are only ever filled with constants from this class.
 */
@Repository
public class FriendshipRepository {

    private final JdbcClient jdbc;

    public FriendshipRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class).toInstant();
    }

    // ------------------------------------------------------------ requests
    /** Empty = no such active user. Otherwise their friend_request_policy. */
    public Optional<String> findRequestPolicy(Long userId) {
        return jdbc.sql("""
                        SELECT COALESCE(p.friend_request_policy, 'EVERYONE')
                        FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id
                        WHERE u.id = :id AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        """)
                .param("id", userId)
                .query(String.class).optional();
    }

    public Optional<Long> findPendingBetween(Long senderId, Long receiverId) {
        return jdbc.sql("""
                        SELECT id FROM friend_requests
                        WHERE sender_id = :s AND receiver_id = :r AND status = 'PENDING'
                        """)
                .param("s", senderId).param("r", receiverId)
                .query(Long.class).optional();
    }

    public Long insertRequest(Long senderId, Long receiverId) {
        return jdbc.sql("""
                        INSERT INTO friend_requests (sender_id, receiver_id) VALUES (:s, :r) RETURNING id
                        """)
                .param("s", senderId).param("r", receiverId)
                .query(Long.class).single();
    }

    /** returns the sender's id if the request was PENDING and addressed to me */
    public Optional<Long> acceptByReceiver(Long requestId, Long me) {
        return transition("ACCEPTED", "receiver_id", "sender_id", requestId, me);
    }

    public Optional<Long> rejectByReceiver(Long requestId, Long me) {
        return transition("REJECTED", "receiver_id", "sender_id", requestId, me);
    }

    /** returns the receiver's id if the request was PENDING and sent by me */
    public Optional<Long> cancelBySender(Long requestId, Long me) {
        return transition("CANCELLED", "sender_id", "receiver_id", requestId, me);
    }

    private Optional<Long> transition(String newStatus, String actorCol, String otherCol, Long id, Long me) {
        return jdbc.sql("""
                        UPDATE friend_requests SET status = '%s', responded_at = now()
                        WHERE id = :id AND %s = :me AND status = 'PENDING'
                        RETURNING %s
                        """.formatted(newStatus, actorCol, otherCol))
                .param("id", id).param("me", me)
                .query(Long.class).optional();
    }

    public boolean isReceiver(Long requestId, Long me) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM friend_requests WHERE id = :id AND receiver_id = :me)")
                .param("id", requestId).param("me", me).query(Boolean.class).single();
    }

    public boolean isSender(Long requestId, Long me) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM friend_requests WHERE id = :id AND sender_id = :me)")
                .param("id", requestId).param("me", me).query(Boolean.class).single();
    }

    public List<FriendRequestView> listPending(Long me, boolean received, int limit, int offset) {
        String myCol = received ? "receiver_id" : "sender_id";
        String otherCol = received ? "sender_id" : "receiver_id";
        return jdbc.sql("""
                        SELECT fr.id, u.id AS other_id, u.username, u.full_name, fr.status, fr.created_at
                        FROM friend_requests fr
                        JOIN users u ON u.id = fr.%s
                        WHERE fr.%s = :me AND fr.status = 'PENDING'
                          AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        ORDER BY fr.created_at DESC, fr.id DESC
                        LIMIT :limit OFFSET :offset
                        """.formatted(otherCol, myCol))
                .param("me", me).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new FriendRequestView(rs.getLong("id"), rs.getLong("other_id"),
                        rs.getString("username"), rs.getString("full_name"),
                        rs.getString("status"), ts(rs, "created_at")))
                .list();
    }

    /** Used when a block happens: pending requests in either direction are cancelled. */
    public int cancelPendingBetween(Long a, Long b) {
        return jdbc.sql("""
                        UPDATE friend_requests SET status = 'CANCELLED', responded_at = now()
                        WHERE status = 'PENDING'
                          AND ((sender_id = :a AND receiver_id = :b) OR (sender_id = :b AND receiver_id = :a))
                        """)
                .param("a", a).param("b", b).update();
    }

    // ---------------------------------------------------------- friendships
    public void insertFriendship(Long a, Long b) {
        jdbc.sql("""
                        INSERT INTO friendships (user_low_id, user_high_id)
                        VALUES (LEAST(:a, :b), GREATEST(:a, :b)) ON CONFLICT DO NOTHING
                        """)
                .param("a", a).param("b", b).update();
    }

    public boolean deleteFriendship(Long a, Long b) {
        return jdbc.sql("""
                        DELETE FROM friendships
                        WHERE user_low_id = LEAST(:a, :b) AND user_high_id = GREATEST(:a, :b)
                        """)
                .param("a", a).param("b", b).update() > 0;
    }

    public Optional<FriendDto> findFriend(Long me, Long friendId) {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.full_name, f.created_at
                        FROM friendships f
                        JOIN users u ON u.id = :friend
                        WHERE f.user_low_id = LEAST(:me, :friend) AND f.user_high_id = GREATEST(:me, :friend)
                        """)
                .param("me", me).param("friend", friendId)
                .query((rs, i) -> new FriendDto(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), ts(rs, "created_at")))
                .optional();
    }

    public List<FriendDto> listFriends(Long me, int limit, int offset) {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.full_name, f.created_at
                        FROM friendships f
                        JOIN users u ON u.id = CASE WHEN f.user_low_id = :me THEN f.user_high_id ELSE f.user_low_id END
                        WHERE (f.user_low_id = :me OR f.user_high_id = :me)
                          AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                        ORDER BY lower(u.username), u.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("me", me).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new FriendDto(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), ts(rs, "created_at")))
                .list();
    }

    public RelationshipInfo relationship(Long me, Long other) {
        if (me.equals(other)) {
            return new RelationshipInfo(Relationship.SELF, null);
        }
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM friendships
                                       WHERE user_low_id = LEAST(:me, :other)
                                         AND user_high_id = GREATEST(:me, :other)) AS friends,
                               (SELECT id FROM friend_requests
                                 WHERE sender_id = :me AND receiver_id = :other AND status = 'PENDING') AS sent_id,
                               (SELECT id FROM friend_requests
                                 WHERE sender_id = :other AND receiver_id = :me AND status = 'PENDING') AS received_id
                        """)
                .param("me", me).param("other", other)
                .query((rs, i) -> {
                    Long sent = (Long) rs.getObject("sent_id");
                    Long received = (Long) rs.getObject("received_id");
                    if (rs.getBoolean("friends")) return new RelationshipInfo(Relationship.FRIENDS, null);
                    if (sent != null) return new RelationshipInfo(Relationship.REQUEST_SENT, sent);
                    if (received != null) return new RelationshipInfo(Relationship.REQUEST_RECEIVED, received);
                    return new RelationshipInfo(Relationship.NONE, null);
                })
                .single();
    }

    // --------------------------------------------------------------- blocks
    public boolean userExists(Long userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE id = :id AND deleted_at IS NULL)")
                .param("id", userId).query(Boolean.class).single();
    }

    public void insertBlock(Long blocker, Long blocked) {
        jdbc.sql("INSERT INTO blocks (blocker_id, blocked_id) VALUES (:a, :b) ON CONFLICT DO NOTHING")
                .param("a", blocker).param("b", blocked).update();
    }

    public void deleteBlock(Long blocker, Long blocked) {
        jdbc.sql("DELETE FROM blocks WHERE blocker_id = :a AND blocked_id = :b")
                .param("a", blocker).param("b", blocked).update();
    }

    public List<BlockedUserDto> listBlocked(Long me, int limit, int offset) {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.full_name, b.created_at
                        FROM blocks b JOIN users u ON u.id = b.blocked_id
                        WHERE b.blocker_id = :me AND u.deleted_at IS NULL
                        ORDER BY b.created_at DESC, u.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("me", me).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new BlockedUserDto(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), ts(rs, "created_at")))
                .list();
    }
}
