package com.vibecircle.discovery;

import com.vibecircle.discovery.dto.DiscoveredUser;
import com.vibecircle.profile.dto.CityDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * All discovery filtering, scoring and ranking happens in one SQL statement, so
 * candidates are never loaded into Java first and ineligible users are never fetched.
 *
 * Jaccard similarity = shared / (mine + theirs - shared) * 100
 */
@Repository
public class DiscoveryRepository {

    private static final String QUERY = """
            WITH me AS (
                SELECT interest_id FROM user_interests WHERE user_id = :me
            ),
            my_count AS (
                SELECT count(*) AS n FROM me
            ),
            candidates AS (
                SELECT u.id, u.username, u.full_name, u.last_seen_at, p.bio,
                       c.id AS city_id, c.name AS city_name, c.state AS city_state, c.country AS city_country
                FROM user_profiles p
                JOIN users u  ON u.id = p.user_id
                JOIN cities c ON c.id = p.city_id
                WHERE p.city_id = :cityId
                  AND u.id <> :me
                  AND u.status = 'ACTIVE'
                  AND u.deleted_at IS NULL
                  AND p.discoverable
                  AND p.profile_visibility = 'PUBLIC'
                  AND p.friend_request_policy = 'EVERYONE'
                  AND NOT EXISTS (SELECT 1 FROM blocks b
                                  WHERE (b.blocker_id = :me AND b.blocked_id = u.id)
                                     OR (b.blocker_id = u.id AND b.blocked_id = :me))
                  AND NOT EXISTS (SELECT 1 FROM friendships f
                                  WHERE f.user_low_id = LEAST(:me, u.id)
                                    AND f.user_high_id = GREATEST(:me, u.id))
                  AND NOT EXISTS (SELECT 1 FROM friend_requests fr
                                  WHERE fr.status = 'PENDING'
                                    AND ((fr.sender_id = :me AND fr.receiver_id = u.id)
                                      OR (fr.sender_id = u.id AND fr.receiver_id = :me)))
                  AND NOT EXISTS (SELECT 1 FROM hidden_recommendations h
                                  WHERE h.user_id = :me AND h.hidden_user_id = u.id)
                  %s
            ),
            scored AS (
                SELECT cand.*,
                       (SELECT count(*) FROM user_interests t WHERE t.user_id = cand.id) AS their_count,
                       (SELECT count(*) FROM user_interests t
                         WHERE t.user_id = cand.id
                           AND t.interest_id IN (SELECT interest_id FROM me)) AS shared
                FROM candidates cand
            )
            SELECT s.id, s.username, s.full_name, s.bio,
                   s.city_id, s.city_name, s.city_state, s.city_country,
                   s.shared,
                   round(100.0 * s.shared
                         / NULLIF((SELECT n FROM my_count) + s.their_count - s.shared, 0), 1) AS similarity
            FROM scored s
            %s
            ORDER BY similarity DESC NULLS LAST, s.shared DESC, s.last_seen_at DESC NULLS LAST, s.id
            LIMIT :limit OFFSET :offset
            """;

    // Fixed SQL fragments only (no user input is ever concatenated into the query).
    private static final String INTEREST_FILTER = """
            AND EXISTS (SELECT 1 FROM user_interests x
                        WHERE x.user_id = u.id AND x.interest_id = :interestId)""";
    private static final String REQUIRE_SHARED = "WHERE s.shared > 0";

    private final JdbcClient jdbc;

    public DiscoveryRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** My city id (nullable) and how many interests I have, or empty if I have no profile row yet. */
    public Optional<MyContext> findMyContext(Long me) {
        return jdbc.sql("""
                        SELECT p.city_id,
                               (SELECT count(*) FROM user_interests WHERE user_id = :me) AS interest_count
                        FROM user_profiles p WHERE p.user_id = :me
                        """)
                .param("me", me)
                .query((rs, i) -> new MyContext(
                        (Long) rs.getObject("city_id"), rs.getInt("interest_count")))
                .optional();
    }

    public List<DiscoveredUser> discover(Long me, Long cityId, Long interestId, int limit, int offset) {
        boolean filterByInterest = interestId != null;
        String sql = QUERY.formatted(filterByInterest ? INTEREST_FILTER : "",
                filterByInterest ? "" : REQUIRE_SHARED);

        var spec = jdbc.sql(sql)
                .param("me", me)
                .param("cityId", cityId)
                .param("limit", limit)
                .param("offset", offset);
        if (filterByInterest) {
            spec = spec.param("interestId", interestId);
        }

        List<DiscoveredUser> rows = spec.query((rs, i) -> new DiscoveredUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("bio"),
                new CityDto(rs.getLong("city_id"), rs.getString("city_name"),
                        rs.getString("city_state"), rs.getString("city_country")),
                List.of(),
                rs.getInt("shared"),
                rs.getDouble("similarity"))).list();

        if (rows.isEmpty()) {
            return rows;
        }

        // Second small query: names of the interests we share, for just this page of users.
        List<Long> ids = rows.stream().map(DiscoveredUser::userId).toList();
        Map<Long, List<String>> sharedByUser = new HashMap<>();
        jdbc.sql("""
                        SELECT ui.user_id, i.name
                        FROM user_interests ui
                        JOIN interests i ON i.id = ui.interest_id
                        WHERE ui.user_id IN (:ids)
                          AND ui.interest_id IN (SELECT interest_id FROM user_interests WHERE user_id = :me)
                        ORDER BY i.name
                        """)
                .param("ids", ids)
                .param("me", me)
                .query((rs, i) -> Map.entry(rs.getLong("user_id"), rs.getString("name")))
                .list()
                .forEach(e -> sharedByUser.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        return rows.stream()
                .map(r -> new DiscoveredUser(r.userId(), r.username(), r.fullName(), r.bio(), r.city(),
                        sharedByUser.getOrDefault(r.userId(), List.of()), r.sharedCount(), r.similarityPercent()))
                .toList();
    }

    public boolean activeUserExists(Long userId) {
        return jdbc.sql("SELECT count(*) FROM users WHERE id = :id AND status = 'ACTIVE' AND deleted_at IS NULL")
                .param("id", userId)
                .query(Long.class).single() > 0;
    }

    public void hide(Long me, Long target) {
        jdbc.sql("""
                        INSERT INTO hidden_recommendations (user_id, hidden_user_id)
                        VALUES (:me, :target) ON CONFLICT DO NOTHING
                        """)
                .param("me", me)
                .param("target", target)
                .update();
    }

    public void unhide(Long me, Long target) {
        jdbc.sql("DELETE FROM hidden_recommendations WHERE user_id = :me AND hidden_user_id = :target")
                .param("me", me)
                .param("target", target)
                .update();
    }

    public record MyContext(Long cityId, int interestCount) {
    }
}
