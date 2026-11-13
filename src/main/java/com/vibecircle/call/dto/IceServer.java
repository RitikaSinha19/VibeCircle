package com.vibecircle.call.dto;

import java.util.List;

/** Same shape as the browser's RTCIceServer. username and credential are null for STUN. */
public record IceServer(List<String> urls, String username, String credential) {
}
