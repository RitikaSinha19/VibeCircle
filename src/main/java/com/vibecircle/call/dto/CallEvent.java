package com.vibecircle.call.dto;

import com.vibecircle.call.SignalType;

/**
 * Pushed to /user/queue/call. type is INCOMING, ACCEPTED, REJECTED, CANCELLED, MISSED, ENDED, FAILED
 * (call filled) or SIGNAL (from, signalType and data filled).
 */
public record CallEvent(String type, Long callId, CallView call, Long from, String signalType, String data) {

    public static CallEvent state(String type, CallView call) {
        return new CallEvent(type, call.id(), call, null, null, null);
    }

    public static CallEvent signal(Long callId, Long from, SignalType signalType, String data) {
        return new CallEvent("SIGNAL", callId, null, from, signalType.name(), data);
    }
}
