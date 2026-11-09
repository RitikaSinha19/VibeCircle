package com.vibecircle.friendship;

/** pendingRequestId is set for REQUEST_SENT / REQUEST_RECEIVED (so the UI can cancel / accept). */
public record RelationshipInfo(Relationship relationship, Long pendingRequestId) {
}
