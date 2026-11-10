package com.vibecircle.post.dto;

import com.vibecircle.post.Audience;
import jakarta.validation.constraints.Size;

/** Fields left out are unchanged. An empty caption ("") clears it. */
public record UpdatePostRequest(@Size(max = 2200) String caption, Audience audience) {
}
