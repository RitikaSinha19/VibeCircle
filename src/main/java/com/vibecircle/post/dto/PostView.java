package com.vibecircle.post.dto;

import com.vibecircle.post.Audience;
import java.time.Instant;
import java.util.List;

public record PostView(
        Long id,
        AuthorDto author,
        String caption,
        Audience audience,
        Long cityId,
        String cityName,
        List<MediaView> media,
        List<String> hashtags,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean mine,
        Instant createdAt) {
}
