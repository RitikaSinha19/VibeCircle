package com.vibecircle.call;

/** What a signalling message carries. The server never looks inside "data"; it only relays it. */
public enum SignalType { OFFER, ANSWER, ICE_CANDIDATE, MEDIA_STATE }
