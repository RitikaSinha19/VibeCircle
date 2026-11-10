package com.vibecircle.comment.dto;

import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

public record CommentView(Long id, Long postId, AuthorDto author, String body, boolean mine, Instant createdAt) {
}
