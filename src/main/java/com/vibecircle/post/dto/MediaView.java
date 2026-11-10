package com.vibecircle.post.dto;

/** url is an authenticated endpoint (GET with the Bearer token), e.g. /api/media/12 */
public record MediaView(Long id, String type, String url) {
}
