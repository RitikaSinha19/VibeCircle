package com.vibecircle.story.dto;

import com.vibecircle.post.Audience;
import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

/**
 * viewCount is only filled for the author's own stories (nobody else learns how many people watched).
 * mediaUrl is an authenticated endpoint (GET with the Bearer token); it is null for text-only stories.
 */
public record StoryView(Long id, AuthorDto author, String text, String mediaType, String mediaUrl,
                        Audience audience, Instant createdAt, Instant expiresAt,
                        boolean viewedByMe, Long viewCount, boolean mine) {
}
