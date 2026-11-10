package com.vibecircle.comment;

import com.vibecircle.comment.dto.CommentView;
import com.vibecircle.post.dto.AuthorDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CommentRepository {

    private static final String SELECT = """
            SELECT c.id, c.post_id, u.id AS author_id, u.username, u.full_name, c.body, c.created_at
            FROM comments c JOIN users u ON u.id = c.author_id
            WHERE c.deleted_at IS NULL AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = c.author_id)
                                 OR (b.blocker_id = c.author_id AND b.blocked_id = :me))
            """;

    private final JdbcClient jdbc;

    public CommentRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static CommentView map(ResultSet rs, Long me) throws SQLException {
        long authorId = rs.getLong("author_id");
        return new CommentView(rs.getLong("id"), rs.getLong("post_id"),
                new AuthorDto(authorId, rs.getString("username"), rs.getString("full_name")),
                rs.getString("body"), me.equals(authorId),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public Long insert(Long postId, Long authorId, String body) {
        return jdbc.sql("INSERT INTO comments (post_id, author_id, body) VALUES (:post, :author, :body) RETURNING id")
                .param("post", postId).param("author", authorId).param("body", body)
                .query(Long.class).single();
    }

    public Optional<CommentView> findView(Long commentId, Long me) {
        return jdbc.sql(SELECT + " AND c.id = :id")
                .param("id", commentId).param("me", me)
                .query((rs, i) -> map(rs, me)).optional();
    }

    public List<CommentView> list(Long postId, Long me, int limit, int offset) {
        return jdbc.sql(SELECT + " AND c.post_id = :post ORDER BY c.created_at, c.id LIMIT :limit OFFSET :offset")
                .param("post", postId).param("me", me).param("limit", limit).param("offset", offset)
                .query((rs, i) -> map(rs, me)).list();
    }

    /** Only your own, not-yet-deleted comment on that post. */
    public boolean softDelete(Long commentId, Long postId, Long authorId) {
        return jdbc.sql("""
                        UPDATE comments SET deleted_at = now()
                        WHERE id = :id AND post_id = :post AND author_id = :author AND deleted_at IS NULL
                        """)
                .param("id", commentId).param("post", postId).param("author", authorId)
                .update() > 0;
    }

    public Optional<Long> postAuthor(Long postId) {
        return jdbc.sql("SELECT author_id FROM posts WHERE id = :id AND deleted_at IS NULL")
                .param("id", postId).query(Long.class).optional();
    }
}
