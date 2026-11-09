package com.vibecircle.discovery.dto;

import com.vibecircle.profile.dto.CityDto;
import java.util.List;

public record DiscoveredUser(
        Long userId,
        String username,
        String fullName,
        String bio,
        CityDto city,
        List<String> sharedInterests,
        int sharedCount,
        double similarityPercent) {
}
