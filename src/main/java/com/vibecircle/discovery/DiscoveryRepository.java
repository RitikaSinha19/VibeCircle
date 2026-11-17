
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

@Repository
public class DiscoveryRepository {

    private static final String QUERY = """
            WITH me AS (
                SELECT interest_id
                FROM user_interests
                WHERE user_id = :me
            ),

            my_count AS (
                SELECT count(*) AS n
                FROM me
            ),

            candidates AS (
                SELECT
                    u.id,
                    u.username,
                    u.full_name,
                    u.last_seen_at,
                    p.bio,
                    c.id AS city_id,
                    c.name AS city_name,
                    c.state AS city_state,
                    c.country AS city_country

                FROM user_profiles p

                JOIN users u
                    ON u.id = p.user_id

                LEFT JOIN cities c
                    ON c.id = p.city_id

                WHERE u.id <> :me
                  AND u.status = 'ACTIVE'
                  AND u.deleted_at IS NULL
                  AND p.discoverable = TRUE
                  AND p.profile_visibility = 'PUBLIC'
                  AND p.friend_request_policy = 'EVERYONE'

                  AND NOT EXISTS (
                      SELECT 1
                      FROM blocks b
                      WHERE
                          (b.blocker_id = :me AND b.blocked_id = u.id)
                          OR
                          (b.blocker_id = u.id AND b.blocked_id = :me)
                  )

                  AND NOT EXISTS (
                      SELECT 1
                      FROM friendships f
                      WHERE f.user_low_id = LEAST(:me, u.id)
                        AND f.user_high_id = GREATEST(:me, u.id)
                  )

                  AND NOT EXISTS (
                      SELECT 1
                      FROM friend_requests fr
                      WHERE fr.status = 'PENDING'
                        AND (
                            (fr.sender_id = :me AND fr.receiver_id = u.id)
                            OR
                            (fr.sender_id = u.id AND fr.receiver_id = :me)
                        )
                  )

                  AND NOT EXISTS (
                      SELECT 1
                      FROM hidden_recommendations h
                      WHERE h.user_id = :me
                        AND h.hidden_user_id = u.id
                  )

                  %s
                  %s
                  %s
            ),

            scored AS (
                SELECT
                    cand.*,

                    (
                        SELECT count(*)
                        FROM user_interests t
                        WHERE t.user_id = cand.id
                    ) AS their_count,

                    (
                        SELECT count(*)
                        FROM user_interests t
                        WHERE t.user_id = cand.id
                          AND t.interest_id IN (
                              SELECT interest_id FROM me
                          )
                    ) AS shared

                FROM candidates cand
            )

            SELECT
                s.id,
                s.username,
                s.full_name,
                s.bio,
                s.city_id,
                s.city_name,
                s.city_state,
                s.city_country,
                s.shared,

                ROUND(
                    100.0 * s.shared /
                    NULLIF(
                        (SELECT n FROM my_count)
                        + s.their_count
                        - s.shared,
                        0
                    ),
                    1
                ) AS similarity

            FROM scored s

            ORDER BY
                similarity DESC NULLS LAST,
                s.shared DESC,
                s.last_seen_at DESC NULLS LAST,
                s.id

            LIMIT :limit OFFSET :offset
            """;

    // These fragments are fixed SQL, never user-provided SQL.
    private static final String CITY_FILTER = """
            AND p.city_id = :cityId
            """;

    private static final String INTEREST_FILTER = """
            AND EXISTS (
                SELECT 1
                FROM user_interests x
                WHERE x.user_id = u.id
                  AND x.interest_id = :interestId
            )
            """;

    private static final String SEARCH_FILTER = """
            AND (
                POSITION(:search IN LOWER(u.username)) > 0
                OR
                POSITION(:search IN LOWER(u.full_name)) > 0
            )
            """;

    private final JdbcClient jdbc;

    public DiscoveryRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * Global people discovery.
     * City, interest and text search are all optional.
     */
    public List<DiscoveredUser> discover(
            Long me,
            Long cityId,
            Long interestId,
            String query,
            int limit,
            int offset) {

        boolean filterByCity = cityId != null;
        boolean filterByInterest = interestId != null;
        boolean filterBySearch = query != null && !query.isBlank();

        String sql = QUERY.formatted(
                filterByCity ? CITY_FILTER : "",
                filterByInterest ? INTEREST_FILTER : "",
                filterBySearch ? SEARCH_FILTER : ""
        );

        var spec = jdbc.sql(sql)
                .param("me", me)
                .param("limit", limit)
                .param("offset", offset);

        if (filterByCity) {
            spec = spec.param("cityId", cityId);
        }

        if (filterByInterest) {
            spec = spec.param("interestId", interestId);
        }

        if (filterBySearch) {
            spec = spec.param("search", query.toLowerCase());
        }

        List<DiscoveredUser> rows = spec.query((rs, i) -> {

            Long cityIdValue = (Long) rs.getObject("city_id");

            CityDto city = cityIdValue == null
                    ? null
                    : new CityDto(
                            cityIdValue,
                            rs.getString("city_name"),
                            rs.getString("city_state"),
                            rs.getString("city_country")
                    );

            return new DiscoveredUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("full_name"),
                    rs.getString("bio"),
                    city,
                    List.of(),
                    rs.getInt("shared"),
                    rs.getDouble("similarity")
            );

        }).list();

        if (rows.isEmpty()) {
            return rows;
        }

        // Fetch shared interest names for only the current page.
        List<Long> ids = rows.stream()
                .map(DiscoveredUser::userId)
                .toList();

        Map<Long, List<String>> sharedByUser = new HashMap<>();

        jdbc.sql("""
                        SELECT ui.user_id, i.name
                        FROM user_interests ui
                        JOIN interests i ON i.id = ui.interest_id
                        WHERE ui.user_id IN (:ids)
                          AND ui.interest_id IN (
                              SELECT interest_id
                              FROM user_interests
                              WHERE user_id = :me
                          )
                        ORDER BY i.name
                        """)
                .param("ids", ids)
                .param("me", me)
                .query((rs, i) ->
                        Map.entry(
                                rs.getLong("user_id"),
                                rs.getString("name")
                        )
                )
                .list()
                .forEach(e ->
                        sharedByUser.computeIfAbsent(
                                e.getKey(),
                                k -> new ArrayList<>()
                        ).add(e.getValue())
                );

        return rows.stream()
                .map(r -> new DiscoveredUser(
                        r.userId(),
                        r.username(),
                        r.fullName(),
                        r.bio(),
                        r.city(),
                        sharedByUser.getOrDefault(
                                r.userId(),
                                List.of()
                        ),
                        r.sharedCount(),
                        r.similarityPercent()
                ))
                .toList();
    }

    public Optional<MyContext> findMyContext(Long me) {

        return jdbc.sql("""
                        SELECT
                            p.city_id,
                            (
                                SELECT count(*)
                                FROM user_interests
                                WHERE user_id = :me
                            ) AS interest_count
                        FROM user_profiles p
                        WHERE p.user_id = :me
                        """)
                .param("me", me)
                .query((rs, i) -> new MyContext(
                        (Long) rs.getObject("city_id"),
                        rs.getInt("interest_count")
                ))
                .optional();
    }

    public boolean activeUserExists(Long userId) {

        return jdbc.sql("""
                        SELECT count(*)
                        FROM users
                        WHERE id = :id
                          AND status = 'ACTIVE'
                          AND deleted_at IS NULL
                        """)
                .param("id", userId)
                .query(Long.class)
                .single() > 0;
    }

    public void hide(Long me, Long target) {

        jdbc.sql("""
                        INSERT INTO hidden_recommendations
                            (user_id, hidden_user_id)
                        VALUES (:me, :target)
                        ON CONFLICT DO NOTHING
                        """)
                .param("me", me)
                .param("target", target)
                .update();
    }

    public void unhide(Long me, Long target) {

        jdbc.sql("""
                        DELETE FROM hidden_recommendations
                        WHERE user_id = :me
                          AND hidden_user_id = :target
                        """)
                .param("me", me)
                .param("target", target)
                .update();
    }

    public record MyContext(Long cityId, int interestCount) {
    }
}
