package com.vibecircle.chat.dto;

/** upToMessageId is optional; leave the body out to mark the whole conversation read. */
public record MarkReadRequest(Long upToMessageId) {
}
