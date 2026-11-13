package com.vibecircle.chat.dto;

import java.util.List;

/** Newest first. Pass nextBefore back as ?before= to load older messages. */
public record MessagePage(List<MessageView> items, Long nextBefore, boolean hasNext) {
}
