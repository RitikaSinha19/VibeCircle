package com.vibecircle.story.dto;

import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

/** One bubble in the story tray: an author with at least one active story the viewer may see. */
public record TrayItem(AuthorDto author, long storyCount, boolean hasUnseen, Instant latestAt) {
}
