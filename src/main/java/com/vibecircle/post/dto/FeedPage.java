package com.vibecircle.post.dto;

import java.util.List;

/** Cursor paging: pass nextCursor back as ?before= to get the next (older) page. */
public record FeedPage(List<PostView> items, Long nextCursor, boolean hasNext) {
}
