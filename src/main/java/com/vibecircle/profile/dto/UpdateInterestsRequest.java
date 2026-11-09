package com.vibecircle.profile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaces the user's whole interest set. An empty list removes all interests. */
public record UpdateInterestsRequest(
        @NotNull @Size(max = 20, message = "at most 20 interests") List<@NotNull Long> interestIds) {
}
