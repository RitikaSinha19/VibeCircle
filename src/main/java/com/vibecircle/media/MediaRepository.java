package com.vibecircle.media;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {

    public record MediaRecord(Long postId, String storageKey, String contentType) {
    }

    private final JdbcClient jdbc;

    public MediaRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Media of a deleted post is treated as missing. */
    public Optional<MediaRecord> find(Long mediaId) {
        return jdbc.sql("""
                        SELECT m.post_id, m.storage_key, m.content_type
                        FROM post_media m JOIN posts p ON p.id = m.post_id
                        WHERE m.id = :id AND p.deleted_at IS NULL
                        """)
                .param("id", mediaId)
                .query((rs, i) -> new MediaRecord(rs.getLong("post_id"), rs.getString("storage_key"),
                        rs.getString("content_type")))
                .optional();
    }
}
