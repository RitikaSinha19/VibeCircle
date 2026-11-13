package com.vibecircle.call.dto;

import java.util.List;

public record IceConfig(List<IceServer> iceServers, long ttlSeconds) {
}
