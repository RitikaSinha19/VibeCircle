package com.vibecircle.profile.dto;

import com.vibecircle.friendship.Relationship;
import com.vibecircle.privacy.ProfileAccess;
import com.vibecircle.profile.ProfileVisibility;
import java.util.List;

/** For access = LIMITED only the identity fields are filled; bio, city and interests are null. */
public record UserProfileView(
        Long userId,
        String username,
        String fullName,
        ProfileAccess access,
        Relationship relationship,
        Long pendingRequestId,
        ProfileVisibility profileVisibility,
        String bio,
        CityDto city,
        List<InterestDto> interests) {
}
