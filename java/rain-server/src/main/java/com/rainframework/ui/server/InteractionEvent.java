package com.rainframework.ui.server;

import com.rainframework.ui.protocol.payload.Payload;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A validated interaction, fired on the server thread. Subscribers that handle it set a result; when none does, the
 * interaction is rejected so the client leaves its pending state.
 */
@Getter
@RequiredArgsConstructor
public final class InteractionEvent {
    private final PlayerRef player;
    private final ScreenInstance instance;
    private final String actionId;
    private final Payload payload;

    private @Nullable InteractionResult result;

    public void setResult(InteractionResult result) {
        this.result = result;
    }
}
