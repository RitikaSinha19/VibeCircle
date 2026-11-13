package com.vibecircle.moderation.dto;

/** Snapshot of the reported item for the reviewer. removed = already deleted / unsent / account deleted. */
public record TargetPreview(String targetType, Long targetId, Long ownerId, String ownerUsername,
                            String ownerStatus, String summary, boolean removed) {
}
