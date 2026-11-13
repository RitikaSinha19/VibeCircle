package com.vibecircle.call;

import com.vibecircle.call.dto.CallView;
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

/**
 * Plain SQL. Every state change is ONE atomic statement guarded by the current status
 * (UPDATE ... WHERE status = 'RINGING' RETURNING id), so two simultaneous accepts or hang-ups cannot both win.
 */
@Repository
public class CallRepository {

    public record OpenCall(Long id, Long callerId, Long calleeId, CallStatus status) {
    }

    private static final String CALL_SELECT = """
            SELECT c.id, c.call_type, c.status, c.created_at, c.answered_at, c.ended_at,
                   cu.id AS caller_id, cu.username AS caller_username, cu.full_name AS caller_name,
                   eu.id AS callee_id, eu.username AS callee_username, eu.full_name AS callee_name,
                   CASE WHEN c.answered_at IS NOT NULL AND c.ended_at IS NOT NULL
                        THEN CAST(extract(epoch FROM (c.ended_at - c.answered_at)) AS bigint) END AS duration
            FROM calls c
            JOIN users cu ON cu.id = c.caller_id
            JOIN users eu ON eu.id = c.callee_id
            WHERE %s
            """;

    private final JdbcClient jdbc;

    public CallRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static CallView map(ResultSet rs, int i) throws SQLException {
        return new CallView(rs.getLong("id"), CallType.valueOf(rs.getString("call_type")),
                CallStatus.valueOf(rs.getString("status")),
                new AuthorDto(rs.getLong("caller_id"), rs.getString("caller_username"), rs.getString("caller_name")),
                new AuthorDto(rs.getLong("callee_id"), rs.getString("callee_username"), rs.getString("callee_name")),
                ts(rs, "created_at"), ts(rs, "answered_at"), ts(rs, "ended_at"), (Long) rs.getObject("duration"));
    }

    // -------------------------------------------------------------- starting
    /**
     * Serialises call setup for these two users until the transaction ends, so "is anyone busy?" and
     * "insert the call" cannot interleave with another call involving the same people (including A->B vs B->A).
     * Locks are taken in id order so two simultaneous setups cannot deadlock.
     */
    public void lockUsers(Long a, Long b) {
        for (Long id : List.of(Math.min(a, b), Math.max(a, b))) {
            jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:id)) locked").param("id", id)
                    .query(Integer.class).single();
        }
    }

    public boolean hasOpenCall(Long userId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM calls
                                       WHERE (caller_id = :u OR callee_id = :u) AND status IN ('RINGING', 'ACTIVE'))
                        """)
                .param("u", userId).query(Boolean.class).single();
    }

    public Long insertRinging(Long caller, Long callee, CallType type) {
        return jdbc.sql("""
                        INSERT INTO calls (caller_id, callee_id, call_type) VALUES (:caller, :callee, :type) RETURNING id
                        """)
                .param("caller", caller).param("callee", callee).param("type", type.name())
                .query(Long.class).single();
    }

    /** The callee had no live connection: record the call as missed straight away. */
    public Long insertMissed(Long caller, Long callee, CallType type) {
        return jdbc.sql("""
                        INSERT INTO calls (caller_id, callee_id, call_type, status, ended_at)
                        VALUES (:caller, :callee, :type, 'MISSED', now()) RETURNING id
                        """)
                .param("caller", caller).param("callee", callee).param("type", type.name())
                .query(Long.class).single();
    }

    // ----------------------------------------------------------------- reads
    public Optional<CallView> findView(Long id) {
        return jdbc.sql(CALL_SELECT.formatted("c.id = :id")).param("id", id).query(CallRepository::map).optional();
    }

    public List<CallView> history(Long me, int limit, int offset) {
        return jdbc.sql(CALL_SELECT.formatted("(c.caller_id = :me OR c.callee_id = :me)")
                        + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset")
                .param("me", me).param("limit", limit).param("offset", offset)
                .query(CallRepository::map).list();
    }

    public boolean isParticipant(Long id, Long me) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM calls WHERE id = :id AND (caller_id = :me OR callee_id = :me))")
                .param("id", id).param("me", me).query(Boolean.class).single();
    }

    public boolean isCaller(Long id, Long me) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM calls WHERE id = :id AND caller_id = :me)")
                .param("id", id).param("me", me).query(Boolean.class).single();
    }

    public boolean isCallee(Long id, Long me) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM calls WHERE id = :id AND callee_id = :me)")
                .param("id", id).param("me", me).query(Boolean.class).single();
    }

    private static OpenCall mapOpen(ResultSet rs, int i) throws SQLException {
        return new OpenCall(rs.getLong("id"), rs.getLong("caller_id"), rs.getLong("callee_id"),
                CallStatus.valueOf(rs.getString("status")));
    }

    public Optional<OpenCall> findOpenCall(Long id) {
        return jdbc.sql("SELECT id, caller_id, callee_id, status FROM calls WHERE id = :id AND status IN ('RINGING', 'ACTIVE')")
                .param("id", id).query(CallRepository::mapOpen).optional();
    }

    public List<OpenCall> findOpen() {
        return jdbc.sql("SELECT id, caller_id, callee_id, status FROM calls WHERE status IN ('RINGING', 'ACTIVE') ORDER BY id")
                .query(CallRepository::mapOpen).list();
    }

    // ----------------------------------------------------------- transitions
    public Optional<Long> accept(Long id, Long calleeId) {
        return jdbc.sql("""
                        UPDATE calls SET status = 'ACTIVE', answered_at = now()
                        WHERE id = :id AND callee_id = :me AND status = 'RINGING' RETURNING id
                        """)
                .param("id", id).param("me", calleeId).query(Long.class).optional();
    }

    public Optional<Long> reject(Long id, Long calleeId) {
        return finish(id, "callee_id = :me", "REJECTED", "RINGING", calleeId);
    }

    public Optional<Long> cancel(Long id, Long callerId) {
        return finish(id, "caller_id = :me", "CANCELLED", "RINGING", callerId);
    }

    public Optional<Long> end(Long id, Long userId) {
        return finish(id, "(caller_id = :me OR callee_id = :me)", "ENDED", "ACTIVE", userId);
    }

    /** Either side reports that the media connection could not be established or broke. */
    public Optional<Long> fail(Long id, Long userId) {
        return jdbc.sql("""
                        UPDATE calls SET status = 'FAILED', ended_at = now()
                        WHERE id = :id AND (caller_id = :me OR callee_id = :me) AND status IN ('RINGING', 'ACTIVE')
                        RETURNING id
                        """)
                .param("id", id).param("me", userId).query(Long.class).optional();
    }

    private Optional<Long> finish(Long id, String who, String to, String from, Long me) {
        return jdbc.sql("UPDATE calls SET status = '%s', ended_at = now() WHERE id = :id AND %s AND status = '%s' RETURNING id"
                        .formatted(to, who, from))
                .param("id", id).param("me", me).query(Long.class).optional();
    }

    // -------------------------------------------------------------- janitor
    public List<Long> expireRinging(double timeoutSeconds) {
        return jdbc.sql("""
                        UPDATE calls SET status = 'MISSED', ended_at = now()
                        WHERE status = 'RINGING' AND created_at < now() - make_interval(secs => :t)
                        RETURNING id
                        """)
                .param("t", timeoutSeconds).query(Long.class).list();
    }

    public List<Long> expireActive(double maxSeconds) {
        return jdbc.sql("""
                        UPDATE calls SET status = 'ENDED', ended_at = now()
                        WHERE status = 'ACTIVE' AND answered_at < now() - make_interval(secs => :t)
                        RETURNING id
                        """)
                .param("t", maxSeconds).query(Long.class).list();
    }

    public Optional<Long> forceEnd(Long id, CallStatus from, CallStatus to) {
        return jdbc.sql("UPDATE calls SET status = :to, ended_at = now() WHERE id = :id AND status = :from RETURNING id")
                .param("to", to.name()).param("from", from.name()).param("id", id)
                .query(Long.class).optional();
    }
}
