package com.vibecircle.post;

/** Published only when a NEW like is recorded on someone else's post. */
public record PostLikedEvent(Long postId, Long likerId, Long authorId) {
}
