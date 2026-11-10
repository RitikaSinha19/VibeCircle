package com.vibecircle.comment;

/** Published only when someone comments on another user's post. */
public record NewCommentEvent(Long commentId, Long postId, Long commenterId, Long postAuthorId) {
}
