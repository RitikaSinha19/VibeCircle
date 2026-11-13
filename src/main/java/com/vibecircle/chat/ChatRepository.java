package com.vibecircle.chat;

import com.vibecircle.chat.dto.ConversationView;
import com.vibecircle.chat.dto.MessageView;
import com.vibecircle.post.dto.AuthorDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL. %s placeholders are only filled with constant fragments from this class. */
@Repository
public class ChatRepository {

    public record UnsendTarget(Long conversationId, String mediaKey) {
    }

    public record MediaRef(Long conversationId, String mediaKey) {
    }

    public record DeliveredRow(Long messageId, Long conversationId, Long senderId) {
    }

    private static final String CONV_SELECT = """
            SELECT c.id, o.id AS other_id, o.username, o.full_name, c.last_message_at, c.created_at,
                   lm.type AS lm_type, lm.body AS lm_body, lm.unsent_at AS lm_unsent, lm.sender_id AS lm_sender,
                   (SELECT count(*) FROM messages m
                     WHERE m.conversation_id = c.id AND m.sender_id <> :me AND m.unsent_at IS NULL
                       AND m.id > COALESCE(mem.last_read_message_id, 0)
                       AND NOT EXISTS (SELECT 1 FROM message_hidden h WHERE h.message_id = m.id AND h.user_id = :me)
                   ) AS unread
            FROM conversation_members mem
            JOIN conversations c ON c.id = mem.conversation_id
            JOIN conversation_members om ON om.conversation_id = c.id AND om.user_id <> :me
            JOIN users o ON o.id = om.user_id
            LEFT JOIN LATERAL (
                SELECT m.type, m.body, m.unsent_at, m.sender_id FROM messages m
                WHERE m.conversation_id = c.id
                  AND NOT EXISTS (SELECT 1 FROM message_hidden h WHERE h.message_id = m.id AND h.user_id = :me)
                ORDER BY m.id DESC LIMIT 1) lm ON TRUE
            WHERE mem.user_id = :me
              AND o.status = 'ACTIVE' AND o.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = o.id)
                                 OR (b.blocker_id = o.id AND b.blocked_id = :me))
              %s
            ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String MSG_SELECT = """
            SELECT m.id, m.conversation_id, m.sender_id, m.type, m.body, (m.media_key IS NOT NULL) AS has_media,
                   m.unsent_at, m.delivered_at, m.client_msg_id, m.created_at,
                   (om.last_read_message_id IS NOT NULL AND om.last_read_message_id >= m.id) AS read_by_other
            FROM messages m
            JOIN conversation_members om ON om.conversation_id = m.conversation_id AND om.user_id <> :me
            WHERE NOT EXISTS (SELECT 1 FROM message_hidden h WHERE h.message_id = m.id AND h.user_id = :me)
              %s
            """;

    private final JdbcClient jdbc;

    public ChatRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    // ------------------------------------------------------------ conversations
    /** Safe under races: the partial unique index uq_conv_direct_pair decides who creates it. */
    public Long getOrCreateDirect(Long a, Long b) {
        long low = Math.min(a, b), high = Math.max(a, b);
        Long id = jdbc.sql("""
                        INSERT INTO conversations (type, direct_low_id, direct_high_id)
                        VALUES ('DIRECT', :low, :high)
                        ON CONFLICT (direct_low_id, direct_high_id) WHERE type = 'DIRECT' DO NOTHING
                        RETURNING id
                        """)
                .param("low", low).param("high", high)
                .query(Long.class).optional()
                .orElseGet(() -> jdbc.sql("""
                                SELECT id FROM conversations
                                WHERE type = 'DIRECT' AND direct_low_id = :low AND direct_high_id = :high
                                """)
                        .param("low", low).param("high", high).query(Long.class).single());
        for (Long user : List.of(a, b)) {
            jdbc.sql("INSERT INTO conversation_members (conversation_id, user_id) VALUES (:c, :u) ON CONFLICT DO NOTHING")
                    .param("c", id).param("u", user).update();
        }
        return id;
    }

    /** The other participant, or empty if I am not a member of this conversation. */
    public Optional<Long> otherMember(Long conversationId, Long me) {
        return jdbc.sql("""
                        SELECT om.user_id FROM conversation_members mem
                        JOIN conversation_members om ON om.conversation_id = mem.conversation_id AND om.user_id <> mem.user_id
                        WHERE mem.conversation_id = :c AND mem.user_id = :me
                        """)
                .param("c", conversationId).param("me", me)
                .query(Long.class).optional();
    }

    public List<ConversationView> listConversations(Long me, int limit, int offset) {
        return jdbc.sql(CONV_SELECT.formatted(""))
                .param("me", me).param("limit", limit).param("offset", offset)
                .query(ChatRepository::mapConversation).list();
    }

    public Optional<ConversationView> findConversation(Long me, Long conversationId) {
        return jdbc.sql(CONV_SELECT.formatted("AND c.id = :cid"))
                .param("me", me).param("cid", conversationId).param("limit", 1).param("offset", 0)
                .query(ChatRepository::mapConversation).optional();
    }

    private static ConversationView mapConversation(ResultSet rs, int i) throws SQLException {
        String preview = null;
        Long lastSender = (Long) rs.getObject("lm_sender");
        if (lastSender != null) {
            if (rs.getObject("lm_unsent") != null) {
                preview = "Message unsent";
            } else if ("IMAGE".equals(rs.getString("lm_type"))) {
                preview = "Photo";
            } else {
                String body = rs.getString("lm_body");
                preview = body != null && body.length() > 80 ? body.substring(0, 80) + "..." : body;
            }
        }
        OffsetDateTime last = rs.getObject("last_message_at", OffsetDateTime.class);
        return new ConversationView(rs.getLong("id"),
                new AuthorDto(rs.getLong("other_id"), rs.getString("username"), rs.getString("full_name")),
                preview, lastSender, last == null ? null : last.toInstant(), rs.getLong("unread"));
    }

    public void touchConversation(Long conversationId) {
        jdbc.sql("UPDATE conversations SET last_message_at = now() WHERE id = :id")
                .param("id", conversationId).update();
    }

    // ------------------------------------------------------------------ messages
    /** Empty = this clientMsgId was already stored for (conversation, sender): a retry. */
    public Optional<Long> insertMessage(Long conversationId, Long senderId, String type, String body,
                                        String mediaKey, UUID clientMsgId, boolean delivered) {
        return jdbc.sql("""
                        INSERT INTO messages (conversation_id, sender_id, type, body, media_key, client_msg_id, delivered_at)
                        VALUES (:c, :sender, :type, :body, :mediaKey, :clientMsgId, CASE WHEN :delivered THEN now() END)
                        ON CONFLICT (conversation_id, sender_id, client_msg_id) WHERE client_msg_id IS NOT NULL
                        DO NOTHING
                        RETURNING id
                        """)
                .param("c", conversationId).param("sender", senderId).param("type", type)
                .param("body", body).param("mediaKey", mediaKey).param("clientMsgId", clientMsgId)
                .param("delivered", delivered)
                .query(Long.class).optional();
    }

    public Optional<Long> findIdByClientMsgId(Long conversationId, Long senderId, UUID clientMsgId) {
        return jdbc.sql("""
                        SELECT id FROM messages
                        WHERE conversation_id = :c AND sender_id = :s AND client_msg_id = :cid
                        """)
                .param("c", conversationId).param("s", senderId).param("cid", clientMsgId)
                .query(Long.class).optional();
    }

    public Optional<MessageView> findMessage(Long me, Long messageId) {
        return jdbc.sql(MSG_SELECT.formatted("AND m.id = :id"))
                .param("me", me).param("id", messageId)
                .query((rs, i) -> mapMessage(rs, me)).optional();
    }

    /** Newest first, keyset paging on the message id. */
    public List<MessageView> history(Long me, Long conversationId, Long before, int limit) {
        String filter = "AND m.conversation_id = :c" + (before != null ? " AND m.id < :before" : "")
                + " ORDER BY m.id DESC LIMIT :limit";
        JdbcClient.StatementSpec spec = jdbc.sql(MSG_SELECT.formatted(filter))
                .param("me", me).param("c", conversationId).param("limit", limit);
        if (before != null) {
            spec = spec.param("before", before);
        }
        return spec.query((rs, i) -> mapMessage(rs, me)).list();
    }

    private static MessageView mapMessage(ResultSet rs, Long me) throws SQLException {
        long id = rs.getLong("id");
        long senderId = rs.getLong("sender_id");
        boolean unsent = rs.getObject("unsent_at") != null;
        String type = rs.getString("type");
        String status = null;
        if (me.equals(senderId)) {
            status = rs.getBoolean("read_by_other") ? "READ" : (rs.getObject("delivered_at") != null ? "DELIVERED" : "SENT");
        }
        String text = unsent || "IMAGE".equals(type) ? null : rs.getString("body");
        String imageUrl = !unsent && rs.getBoolean("has_media") ? "/api/chat/media/" + id : null;
        return new MessageView(id, rs.getLong("conversation_id"), senderId, type, text, imageUrl, unsent,
                status, rs.getObject("client_msg_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    /** How many messages from `sender` has `recipient` not read yet (including the one just inserted)? */
    public long unreadFromSender(Long conversationId, Long sender, Long recipient) {
        return jdbc.sql("""
                        SELECT count(*) FROM messages m
                        WHERE m.conversation_id = :c AND m.sender_id = :sender AND m.unsent_at IS NULL
                          AND m.id > COALESCE((SELECT last_read_message_id FROM conversation_members
                                               WHERE conversation_id = :c AND user_id = :recipient), 0)
                        """)
                .param("c", conversationId).param("sender", sender).param("recipient", recipient)
                .query(Long.class).single();
    }

    /**
     * Moves my read cursor forward to the newest message from the other person that is <= limitId.
     * Empty = nothing new to mark. Also stamps those messages as delivered.
     */
    public Optional<Long> markRead(Long conversationId, Long me, long limitId) {
        Optional<Long> cursor = jdbc.sql("""
                        UPDATE conversation_members cm SET last_read_message_id = t.upto
                        FROM (SELECT max(id) AS upto FROM messages
                              WHERE conversation_id = :c AND sender_id <> :me AND id <= :limitId) t
                        WHERE cm.conversation_id = :c AND cm.user_id = :me AND t.upto IS NOT NULL
                          AND (cm.last_read_message_id IS NULL OR cm.last_read_message_id < t.upto)
                        RETURNING t.upto
                        """)
                .param("c", conversationId).param("me", me).param("limitId", limitId)
                .query(Long.class).optional();
        cursor.ifPresent(upTo -> jdbc.sql("""
                        UPDATE messages SET delivered_at = now()
                        WHERE conversation_id = :c AND sender_id <> :me AND id <= :upTo AND delivered_at IS NULL
                        """)
                .param("c", conversationId).param("me", me).param("upTo", upTo).update());
        return cursor;
    }

    /** When a user comes online, everything waiting for them counts as delivered. */
    public List<DeliveredRow> markDeliveredFor(Long userId) {
        return jdbc.sql("""
                        UPDATE messages m SET delivered_at = now()
                        FROM conversation_members cm
                        WHERE cm.conversation_id = m.conversation_id AND cm.user_id = :me
                          AND m.sender_id <> :me AND m.delivered_at IS NULL AND m.unsent_at IS NULL
                        RETURNING m.id, m.conversation_id, m.sender_id
                        """)
                .param("me", userId)
                .query((rs, i) -> new DeliveredRow(rs.getLong("id"), rs.getLong("conversation_id"),
                        rs.getLong("sender_id")))
                .list();
    }

    // --------------------------------------------------------- unsend / hide / media
    /** Row-locks my own, not-yet-unsent message. Empty = missing, not mine, or already unsent. */
    public Optional<UnsendTarget> lockOwnMessage(Long messageId, Long me) {
        return jdbc.sql("""
                        SELECT conversation_id, media_key FROM messages
                        WHERE id = :id AND sender_id = :me AND unsent_at IS NULL FOR UPDATE
                        """)
                .param("id", messageId).param("me", me)
                .query((rs, i) -> new UnsendTarget(rs.getLong("conversation_id"), rs.getString("media_key")))
                .optional();
    }

    /** body = '' keeps the table's "body or media" CHECK satisfied once the image is gone. */
    public void unsend(Long messageId) {
        jdbc.sql("UPDATE messages SET unsent_at = now(), body = '', media_key = NULL WHERE id = :id")
                .param("id", messageId).update();
    }

    public Optional<Long> conversationOfMessage(Long messageId) {
        return jdbc.sql("SELECT conversation_id FROM messages WHERE id = :id")
                .param("id", messageId).query(Long.class).optional();
    }

    public void hideMessage(Long messageId, Long me) {
        jdbc.sql("INSERT INTO message_hidden (message_id, user_id) VALUES (:id, :me) ON CONFLICT DO NOTHING")
                .param("id", messageId).param("me", me).update();
    }

    public Optional<MediaRef> findMedia(Long messageId) {
        return jdbc.sql("""
                        SELECT conversation_id, media_key FROM messages
                        WHERE id = :id AND media_key IS NOT NULL AND unsent_at IS NULL
                        """)
                .param("id", messageId)
                .query((rs, i) -> new MediaRef(rs.getLong("conversation_id"), rs.getString("media_key")))
                .optional();
    }
}
