package com.rainframework.ui.client.screen;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import com.rainframework.ui.client.expand.Expander;
import com.rainframework.ui.client.layout.LaidOutNode;
import com.rainframework.ui.client.layout.LayoutEngine;
import com.rainframework.ui.client.layout.Measurer;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.packet.Interact;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * One open Rain screen on the client. It owns the pending state: after a click every button ignores input until the
 * server answers (update, rejection or close) or {@link #PENDING_TIMEOUT_NANOS} passes, so a double click sends one
 * interaction.
 */
public final class ScreenController {
    public static final long PENDING_TIMEOUT_NANOS = 5_000_000_000L;

    @Getter
    private final int instanceId;
    @Getter
    private final String screenId;
    @Getter
    private final Contract contract;

    private final Expander expander;
    private final LongSupplier nanoClock;
    private final Painter painter = new Painter();

    @Getter
    private int revision;
    private RenderNode.Box tree;
    private @Nullable LaidOutNode layout;
    private long pendingSince = -1;

    public ScreenController(
            int instanceId,
            String screenId,
            Contract contract,
            int revision,
            JsonNode properties,
            LongSupplier nanoClock
    ) {
        this.instanceId = instanceId;
        this.screenId = screenId;
        this.contract = contract;
        this.expander = new Expander(contract);
        this.nanoClock = nanoClock;
        this.revision = revision;
        this.tree = expander.expand(properties);
    }

    public void layout(Measurer measurer, int screenWidth, int screenHeight) {
        layout = new LayoutEngine(measurer).layout(tree, screenWidth, screenHeight);
    }

    public List<DrawCommand> draw(double mouseX, double mouseY) {
        if (layout == null) {
            return List.of();
        }

        return painter.paint(layout, mouseX, mouseY, isPending());
    }

    /** The interaction to send for a click, or null when the click hits no enabled button or one is pending. */
    public @Nullable Interact click(double x, double y) {
        if (layout == null || isPending()) {
            return null;
        }

        final var button = findButton(layout, x, y);
        if (button == null || button.disabled()) {
            return null;
        }

        pendingSince = nanoClock.getAsLong();
        return new Interact(instanceId, revision, button.actionId(), button.payloadJson());
    }

    /** Properties must already be validated; the layout is kept stale until the next {@link #layout} call. */
    public void update(int newRevision, JsonNode properties) {
        revision = newRevision;
        tree = expander.expand(properties);
        layout = null;
        pendingSince = -1;
    }

    public void onRejected() {
        pendingSince = -1;
    }

    public boolean isPending() {
        return pendingSince >= 0 && nanoClock.getAsLong() - pendingSince < PENDING_TIMEOUT_NANOS;
    }

    public boolean needsLayout() {
        return layout == null;
    }

    // The deepest button under the point: buttons can contain other buttons' content but not other buttons in v0.
    private static RenderNode.@Nullable Button findButton(LaidOutNode node, double x, double y) {
        if (!node.contains(x, y)) {
            return null;
        }

        if (node.node() instanceof RenderNode.Button button) {
            return button;
        }

        for (final var child : node.children()) {
            final var found = findButton(child, x, y);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
