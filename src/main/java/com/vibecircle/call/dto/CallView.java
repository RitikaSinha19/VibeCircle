package com.vibecircle.call.dto;

import com.vibecircle.call.CallStatus;
import com.vibecircle.call.CallType;
import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

/** durationSeconds = time between answer and end; null if the call was never answered or is still running. */
public record CallView(Long id, CallType type, CallStatus status, AuthorDto caller, AuthorDto callee,
                       Instant createdAt, Instant answeredAt, Instant endedAt, Long durationSeconds) {
}
