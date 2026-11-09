package com.vibecircle.profile.dto;

import com.vibecircle.profile.FriendRequestPolicy;
import com.vibecircle.profile.ProfileVisibility;
import jakarta.validation.constraints.Size;

/** Partial update: any field left out (null) is unchanged. An empty bio ("") clears it. */
public record UpdateProfileRequest(
        @Size(max = 300) String bio,
        Long cityId,
        ProfileVisibility profileVisibility,
        Boolean discoverable,
        FriendRequestPolicy friendRequestPolicy) {
}
