package com.vibecircle.profile.dto;

import com.vibecircle.profile.FriendRequestPolicy;
import com.vibecircle.profile.ProfileVisibility;
import java.util.List;

public record ProfileResponse(
        Long userId,
        String username,
        String fullName,
        String bio,
        CityDto city,
        ProfileVisibility profileVisibility,
        boolean discoverable,
        FriendRequestPolicy friendRequestPolicy,
        List<InterestDto> interests,
        boolean profileComplete) {
}
