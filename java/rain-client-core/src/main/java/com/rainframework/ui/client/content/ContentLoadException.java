package com.rainframework.ui.client.content;

import com.rainframework.ui.protocol.packet.ScreenFailureReason;
import lombok.Getter;

/** Why a screen's content could not be made available; {@link #getReason()} goes back to the server. */
@Getter
public final class ContentLoadException extends RuntimeException {
    private final ScreenFailureReason reason;

    public ContentLoadException(ScreenFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
