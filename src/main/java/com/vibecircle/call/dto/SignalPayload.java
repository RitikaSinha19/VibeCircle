package com.vibecircle.call.dto;

import com.vibecircle.call.SignalType;

/** STOMP body for /app/call/signal. data is the SDP or ICE candidate as a JSON string, or the media state. */
public record SignalPayload(Long callId, SignalType type, String data) {
}
