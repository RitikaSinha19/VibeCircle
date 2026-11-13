package com.vibecircle.call;

/**
 * RINGING -> ACTIVE | REJECTED | CANCELLED | MISSED | FAILED
 * ACTIVE  -> ENDED | FAILED
 * Everything except RINGING and ACTIVE is final. Matches the CHECK constraint on calls.status.
 */
public enum CallStatus { RINGING, ACTIVE, ENDED, REJECTED, MISSED, CANCELLED, FAILED }
