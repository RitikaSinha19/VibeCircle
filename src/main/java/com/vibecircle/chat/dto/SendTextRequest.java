package com.vibecircle.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** clientMsgId (optional) makes a retry safe: sending the same id twice stores the message once. */
public record SendTextRequest(@NotBlank @Size(max = 4000) String text, UUID clientMsgId) {
}
