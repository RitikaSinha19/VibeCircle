package com.vibecircle.call;

import com.vibecircle.call.dto.CallView;

/** Published inside the transaction; pushed to clients only after it commits. */
public record CallStateEvent(String type, CallView call) {
}
