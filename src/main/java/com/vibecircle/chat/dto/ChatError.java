package com.vibecircle.chat.dto;

/** Sent to /user/queue/errors when a STOMP message is rejected. */
public record ChatError(String message) {
}
