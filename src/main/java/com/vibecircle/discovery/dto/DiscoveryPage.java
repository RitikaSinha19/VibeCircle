package com.vibecircle.discovery.dto;

import java.util.List;

public record DiscoveryPage(List<DiscoveredUser> items, int page, int size, boolean hasNext) {
}
