package com.vibecircle.profile.dto;

import java.util.List;

public record InterestGroupDto(String category, List<InterestDto> interests) {
}
