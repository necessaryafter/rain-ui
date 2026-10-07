package com.rainframework.ui.client.screen;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import com.rainframework.ui.client.expand.Expander;
import com.rainframework.ui.client.layout.Bounds;
import com.rainframework.ui.client.layout.LaidOutNode;
import com.rainframework.ui.client.layout.LayoutEngine;
import com.rainframework.ui.client.layout.Measurer;
import com.rainframework.ui.client.layout.Transform2D;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.Limits;
import com.rainframework.ui.protocol.packet.Interact;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private JsonNode properties;
    private @Nullable LaidOutNode layout;
    private Bounds viewport = new Bounds(0, 0, 0, 0);
    private long pendingSince = -1;
    private int updateEpoch;
    private final Map<String, String> inputValues = new HashMap<>();
    private final Map<String, String> selectedTabs = new HashMap<>();
    private final Map<String, int[]> scrollOffsets = new HashMap<>();
    private @Nullable String focusedInputStateId;
    private int focusedInputMaxLength = 4096;
    private boolean focusedInputMultiline;

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
        this.properties = properties;
        this.tree = expander.expand(properties, inputValues, selectedTabs, scrollOffsets, updateEpoch);
    }

    public void layout(Measurer measurer, int screenWidth, int screenHeight) {
        final var engine = new LayoutEngine(measurer);
        viewport = new Bounds(0, 0, screenWidth, screenHeight);
        layout = engine.layout(tree, screenWidth, screenHeight);

        if (clampScrollOffsets(layout)) {
            tree = expand();
            layout = engine.layout(tree, screenWidth, screenHeight);
        }
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

        final var control = findControl(layout, viewport, Transform2D.IDENTITY, x, y);
        if (control instanceof RenderNode.Input input) {
            focusedInputStateId = input.stateId();
            focusedInputMaxLength = input.maxLength();
            focusedInputMultiline = input.multiline();

            return null;
        }

        if (control instanceof RenderNode.TabHeader tab) {
            selectedTabs.put(tab.tabsId(), tab.id());
            tree = expand();
            retainVisibleFocus();
            layout = null;

            return null;
        }

        if (!(control instanceof RenderNode.Button button) || button.disabled()) {
            return null;
        }

        final var payload = button.resolvedPayload();
        if (payload.getBytes(StandardCharsets.UTF_8).length > Limits.MAX_PAYLOAD_BYTES) {
            return null;
        }

        pendingSince = nanoClock.getAsLong();

        return new Interact(instanceId, revision, button.actionId(), payload);
    }

    /** Properties must already be validated; the layout is kept stale until the next {@link #layout} call. */
    public void update(int newRevision, JsonNode properties) {
        final var nextEpoch = updateEpoch + 1;
        final var nextTree = expander.expand(properties, inputValues, selectedTabs, scrollOffsets, nextEpoch);

        revision = newRevision;
        this.properties = properties;
        updateEpoch = nextEpoch;
        tree = nextTree;
        layout = null;
        pendingSince = -1;
        retainVisibleFocus();
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

    public void setInputValue(String id, String value) {
        inputValues.put("screen/input:" + id, truncateCodePoints(value, 4096));
        tree = expand();
        layout = null;
    }

    public @Nullable String inputValue(String id) {
        return inputValues.get("screen/input:" + id);
    }

    public boolean typeInput(char character) {
        return typeInput(String.valueOf(character));
    }

    public boolean typeInput(String text) {
        if (focusedInputStateId == null || text.isEmpty()
                || text.codePoints().anyMatch(character -> Character.isISOControl(character)
                        && !(character == '\n' && focusedInputMultiline))) {
            return false;
        }

        final var existing = inputValues.getOrDefault(focusedInputStateId, "");
        final var remaining = focusedInputMaxLength - existing.codePointCount(0, existing.length());
        if (remaining <= 0) {
            return false;
        }

        final var inserted = truncateCodePoints(text, remaining);
        if (inserted.isEmpty()) {
            return false;
        }

        inputValues.put(focusedInputStateId, existing + inserted);
        tree = expand();
        layout = null;

        return true;
    }

    public boolean backspaceInput() {
        if (focusedInputStateId == null) {
            return false;
        }

        final var existing = inputValues.getOrDefault(focusedInputStateId, "");
        if (existing.isEmpty()) {
            return false;
        }

        inputValues.put(focusedInputStateId,
                existing.substring(0, existing.offsetByCodePoints(existing.length(), -1)));
        tree = expand();
        layout = null;

        return true;
    }

    public boolean newlineInput() {
        return focusedInputMultiline && typeInput('\n');
    }

    /** Moves focus through currently visible inputs without notifying the server. */
    public boolean focusNextInput(boolean backwards) {
        if (layout == null) {
            return false;
        }

        final var inputs = new ArrayList<RenderNode.Input>();
        collectVisibleInputs(layout, viewport, Transform2D.IDENTITY, inputs);
        if (inputs.isEmpty()) {
            return false;
        }

        int current = -1;

        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).stateId().equals(focusedInputStateId)) {
                current = i;
                break;
            }
        }

        final var next = Math.floorMod(current + (backwards ? -1 : 1), inputs.size());
        final var input = inputs.get(next);
        focusedInputStateId = input.stateId();
        focusedInputMaxLength = input.maxLength();
        focusedInputMultiline = input.multiline();

        return true;
    }

    public void selectTab(String defaultTab, String selectedTab) {
        final var tabsId = findTabsId(tree, defaultTab);
        if (tabsId == null) {
            return;
        }

        selectedTabs.put(tabsId, selectedTab);
        tree = expand();
        retainVisibleFocus();
        layout = null;
    }

    private RenderNode.Box expand() {
        return expander.expand(properties, inputValues, selectedTabs, scrollOffsets, updateEpoch);
    }

    private void retainVisibleFocus() {
        if (focusedInputStateId != null && !containsInput(tree, focusedInputStateId)) {
            focusedInputStateId = null;
        }
    }

    private static boolean containsInput(RenderNode node, String stateId) {
        return switch (node) {
            case RenderNode.Input input -> input.stateId().equals(stateId);
            case RenderNode.Box box -> containsInput(box.children(), stateId);
            case RenderNode.Grid grid -> containsInput(grid.children(), stateId);
            case RenderNode.Scroll scroll -> containsInput(scroll.content(), stateId);
            case RenderNode.Tabs tabs -> containsInput(tabs.content(), stateId);
            case RenderNode.Button button -> containsInput(button.content(), stateId);
            default -> false;
        };
    }

    private static boolean containsInput(List<? extends RenderNode> nodes, String stateId) {
        for (final var node : nodes) {
            if (containsInput(node, stateId)) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable String findTabsId(RenderNode node, String defaultTab) {
        return switch (node) {
            case RenderNode.Tabs tabs -> tabs.headers().stream().anyMatch(header -> header.id().equals(defaultTab))
                    ? tabs.id() : findTabsId(tabs.content(), defaultTab);
            case RenderNode.Box box -> findTabsId(box.children(), defaultTab);
            case RenderNode.Grid grid -> findTabsId(grid.children(), defaultTab);
            case RenderNode.Scroll scroll -> findTabsId(scroll.content(), defaultTab);
            case RenderNode.Button button -> findTabsId(button.content(), defaultTab);
            default -> null;
        };
    }

    private static @Nullable String findTabsId(List<? extends RenderNode> nodes, String defaultTab) {
        for (final var node : nodes) {
            final var id = findTabsId(node, defaultTab);
            if (id != null) {
                return id;
            }
        }

        return null;
    }

    public boolean scroll(double x, double y, double horizontal, double vertical) {
        if (layout == null) {
            return false;
        }

        final var target = findScroll(layout, viewport, Transform2D.IDENTITY, x, y);
        if (target == null || target.children().isEmpty()) {
            return false;
        }

        final var scroll = (RenderNode.Scroll) target.node();
        final var content = target.children().getFirst();
        final var visible = target.innerBounds();
        final var offset = scrollOffsets.computeIfAbsent(scroll.id(), ignored -> new int[2]);

        if (!scroll.direction().equals("horizontal")) {
            offset[1] = clamp(offset[1] - (int) Math.round(vertical * 12),
                    0, Math.max(0, content.height() - visible.height()));
        }

        if (!scroll.direction().equals("vertical")) {
            offset[0] = clamp(offset[0] - (int) Math.round(horizontal * 12),
                    0, Math.max(0, content.width() - visible.width()));
        }

        tree = expand();
        layout = null;

        return true;
    }

    private boolean clampScrollOffsets(LaidOutNode node) {
        boolean changed = false;
        if (node.node() instanceof RenderNode.Scroll scroll && !node.children().isEmpty()) {
            final var offset = scrollOffsets.get(scroll.id());
            if (offset != null) {
                final var content = node.children().getFirst();
                final var visible = node.innerBounds();
                final var maxX = scroll.direction().equals("vertical")
                        ? 0 : Math.max(0, content.width() - visible.width());
                final var maxY = scroll.direction().equals("horizontal")
                        ? 0 : Math.max(0, content.height() - visible.height());
                final var x = clamp(offset[0], 0, maxX);
                final var y = clamp(offset[1], 0, maxY);
                changed = x != offset[0] || y != offset[1];
                offset[0] = x;
                offset[1] = y;
            }
        }

        for (final var child : node.children()) {
            changed |= clampScrollOffsets(child);
        }

        return changed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String truncateCodePoints(String value, int maximum) {
        if (value.codePointCount(0, value.length()) <= maximum) {
            return value;
        }

        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private static void collectVisibleInputs(
            LaidOutNode node,
            Bounds visible,
            Transform2D ancestor,
            List<RenderNode.Input> inputs
    ) {
        if (!visible.hasArea()) {
            return;
        }

        final var world = transform(node, ancestor);
        if (node.node() instanceof RenderNode.Input input
                && visible.intersect(world.map(node.bounds())).hasArea()) {
            inputs.add(input);
        }

        final var childVisible = childClip(node, visible, world);

        for (final var child : node.children()) {
            collectVisibleInputs(child, childVisible, world, inputs);
        }
    }

    private static Transform2D transform(LaidOutNode node, Transform2D ancestor) {
        return ancestor.compose(Transform2D.around(node.node().style().transform(), node.bounds()));
    }

    private static boolean contains(Bounds bounds, Transform2D world, double x, double y) {
        final var point = world.inverse(x, y);

        return point != null && bounds.contains(point.x(), point.y());
    }

    private static Bounds childClip(LaidOutNode node, Bounds visible, Transform2D world) {
        if (node.node() instanceof RenderNode.Scroll || node.node().style().clipChildren()) {
            return visible.intersect(world.map(node.innerBounds()));
        }

        return visible;
    }

    private static @Nullable LaidOutNode findScroll(
            LaidOutNode node,
            Bounds visible,
            Transform2D ancestor,
            double x,
            double y
    ) {
        if (!visible.contains(x, y)) {
            return null;
        }

        final var world = transform(node, ancestor);
        final var children = paintOrder(node.children());
        final var childVisible = childClip(node, visible, world);

        for (int i = children.size() - 1; i >= 0; i--) {
            final var nested = findScroll(children.get(i), childVisible, world, x, y);
            if (nested != null) {
                return nested;
            }
        }

        return node.node() instanceof RenderNode.Scroll && contains(node.innerBounds(), world, x, y) ? node : null;
    }

    private static @Nullable RenderNode findControl(
            LaidOutNode node,
            Bounds visible,
            Transform2D ancestor,
            double x,
            double y
    ) {
        if (!visible.contains(x, y)) {
            return null;
        }

        final var world = transform(node, ancestor);
        final var children = paintOrder(node.children());
        final var childVisible = childClip(node, visible, world);

        for (int i = children.size() - 1; i >= 0; i--) {
            final var found = findControl(children.get(i), childVisible, world, x, y);
            if (found != null) {
                return found;
            }
        }

        if (!contains(node.bounds(), world, x, y)) {
            return null;
        }

        return switch (node.node()) {
            case RenderNode.Input input -> input;
            case RenderNode.TabHeader tab -> tab;
            case RenderNode.Button button -> button;
            default -> null;
        };
    }

    private static List<LaidOutNode> paintOrder(List<LaidOutNode> children) {
        return children.stream().sorted(Comparator.comparingInt(child -> child.node().zIndex())).toList();
    }
}
