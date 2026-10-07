package com.rainframework.ui.client.draw;

import com.rainframework.ui.client.layout.Bounds;
import com.rainframework.ui.client.layout.LaidOutNode;
import com.rainframework.ui.client.layout.Transform2D;
import com.rainframework.ui.client.render.RenderNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns a laid-out screen into draw commands, including the panel behind it and the button states. */
public final class Painter {
    public static final int PANEL_COLOR = 0xC0101010;
    public static final int BUTTON_COLOR = 0xFF3A3A3A;
    public static final int BUTTON_HOVER_COLOR = 0xFF505050;
    public static final int BUTTON_DISABLED_COLOR = 0xFF202020;
    public static final int BUTTON_PENDING_COLOR = 0xFF2A2A48;
    public static final int DISABLED_TEXT_COLOR = 0xFF808080;
    public static final int SCROLLBAR_TRACK_COLOR = 0x80303030;
    public static final int SCROLLBAR_THUMB_COLOR = 0xC0B0B0B0;
    private static final int SCROLLBAR_THICKNESS = 3;

    public List<DrawCommand> paint(LaidOutNode root, double mouseX, double mouseY, boolean pending) {
        final var commands = new ArrayList<DrawCommand>();
        paintNode(root, mouseX, mouseY, pending, false, 1.0F,
                null, Transform2D.IDENTITY, true, commands);

        return commands;
    }

    private void paintNode(
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            boolean dimmed,
            float opacity,
            @Nullable Bounds visible,
            Transform2D ancestor,
            boolean root,
            List<DrawCommand> commands
    ) {
        final var style = laidOut.node().style();
        final var subtreeOpacity = opacity * style.opacity();
        final var local = Transform2D.around(style.transform(), laidOut.bounds());
        final var world = ancestor.compose(local);

        if (local != Transform2D.IDENTITY) {
            commands.add(new DrawCommand.PushTransform(local));
        }

        if (root && laidOut.node() instanceof RenderNode.Box box
                && box.legacyPanel() && !style.hasBackground()) {
            commands.add(new DrawCommand.FillRect(
                    laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(),
                    applyOpacity(PANEL_COLOR, subtreeOpacity)));
        }

        switch (laidOut.node()) {
            case RenderNode.Text text -> commands.add(new DrawCommand.DrawText(
                    text.value(),
                    laidOut.x(),
                    laidOut.y(),
                    applyOpacity(dimmed ? DISABLED_TEXT_COLOR : text.color(), subtreeOpacity),
                    text.shadow(),
                    text.fontHash()));
            case RenderNode.Item item -> commands.add(new DrawCommand.DrawItem(
                    item.itemData(),
                    laidOut.x(),
                    laidOut.y(),
                    item.size()));
            case RenderNode.Image image -> {
                if (style.hasBackground()) {
                    commands.add(new DrawCommand.FillRect(
                            laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(),
                            applyOpacity(style.backgroundColor(), subtreeOpacity)));
                }

                commands.add(new DrawCommand.DrawImage(
                        image.hash(), laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(), subtreeOpacity));

                if (style.hasBorder()) {
                    paintBorder(laidOut, style, subtreeOpacity, commands);
                }
            }
            case RenderNode.Input input -> {
                commands.add(new DrawCommand.FillRect(
                        laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(),
                        applyOpacity(BUTTON_COLOR, subtreeOpacity)));
                final var value = input.value().isEmpty() ? input.placeholder() : input.value();
                commands.add(new DrawCommand.DrawText(
                        value,
                        laidOut.x() + 3,
                        laidOut.y() + 5,
                        applyOpacity(input.value().isEmpty() ? DISABLED_TEXT_COLOR : 0xFFFFFFFF, subtreeOpacity),
                        false,
                        null));
            }
            case RenderNode.Button button -> {
                final var color = buttonColor(button, laidOut, mouseX, mouseY, pending, visible, world);
                final var background = style.hasBackground() && !button.disabled() && !pending
                        ? style.backgroundColor() : color;
                commands.add(new DrawCommand.FillRect(
                        laidOut.x(),
                        laidOut.y(),
                        laidOut.width(),
                        laidOut.height(),
                        applyOpacity(background, subtreeOpacity)));

                if (style.hasBorder()) {
                    paintBorder(laidOut, style, subtreeOpacity, commands);
                }

                paintClippedChildren(laidOut, mouseX, mouseY, pending, dimmed || button.disabled(),
                        subtreeOpacity, visible, world, style.clipChildren(), commands);
            }
            case RenderNode.Box ignored -> {
                paintSurface(laidOut, style, subtreeOpacity, commands);
                paintClippedChildren(laidOut, mouseX, mouseY, pending, dimmed, subtreeOpacity,
                        visible, world, style.clipChildren(), commands);
            }
            case RenderNode.Grid ignored -> {
                paintSurface(laidOut, style, subtreeOpacity, commands);
                paintClippedChildren(laidOut, mouseX, mouseY, pending, dimmed, subtreeOpacity,
                        visible, world, style.clipChildren(), commands);
            }
            case RenderNode.Scroll scroll -> {
                paintSurface(laidOut, style, subtreeOpacity, commands);
                paintClippedChildren(laidOut, mouseX, mouseY, pending, dimmed, subtreeOpacity,
                        visible, world, true, commands);
                paintScrollbars(laidOut, scroll, subtreeOpacity, commands);
            }
            case RenderNode.Tabs ignored -> paintChildren(laidOut, mouseX, mouseY, pending, dimmed, subtreeOpacity,
                    visible, world, commands);
            case RenderNode.TabHeader header -> {
                commands.add(new DrawCommand.FillRect(
                        laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(),
                        applyOpacity(header.selected() ? BUTTON_HOVER_COLOR : BUTTON_COLOR, subtreeOpacity)));
                commands.add(new DrawCommand.DrawText(
                        header.label(), laidOut.x() + 6, laidOut.y() + 6,
                        applyOpacity(0xFFFFFFFF, subtreeOpacity), false, null));
            }
        }

        if (local != Transform2D.IDENTITY) {
            commands.add(new DrawCommand.PopTransform());
        }
    }

    private static void paintSurface(
            LaidOutNode laidOut,
            RenderNode.VisualStyle style,
            float opacity,
            List<DrawCommand> commands
    ) {
        if (style.hasBackground()) {
            commands.add(new DrawCommand.FillRect(
                    laidOut.x(), laidOut.y(), laidOut.width(), laidOut.height(),
                    applyOpacity(style.backgroundColor(), opacity)));
        }

        if (style.hasBorder()) {
            paintBorder(laidOut, style, opacity, commands);
        }
    }

    private void paintClippedChildren(
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            boolean dimmed,
            float opacity,
            @Nullable Bounds visible,
            Transform2D world,
            boolean clipChildren,
            List<DrawCommand> commands
    ) {
        if (!clipChildren) {
            paintChildren(laidOut, mouseX, mouseY, pending, dimmed, opacity, visible, world, commands);

            return;
        }

        final var inner = world.map(laidOut.innerBounds());
        commands.add(new DrawCommand.PushClip(inner.x(), inner.y(), inner.width(), inner.height()));
        paintChildren(laidOut, mouseX, mouseY, pending, dimmed, opacity, clip(visible, inner), world, commands);
        commands.add(new DrawCommand.PopClip());
    }

    private void paintChildren(
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            boolean dimmed,
            float opacity,
            @Nullable Bounds visible,
            Transform2D world,
            List<DrawCommand> commands
    ) {
        for (final var child : paintOrder(laidOut.children())) {
            paintNode(child, mouseX, mouseY, pending, dimmed, opacity, visible, world, false, commands);
        }
    }

    private static Bounds clip(@Nullable Bounds visible, Bounds inner) {
        return visible == null ? inner : visible.intersect(inner);
    }

    private static List<LaidOutNode> paintOrder(List<LaidOutNode> children) {
        return children.stream().sorted(Comparator.comparingInt(child -> child.node().zIndex())).toList();
    }

    // While an interaction is pending every button shows it, since none of them accept clicks until the reply.
    private static int buttonColor(
            RenderNode.Button button,
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            @Nullable Bounds visible,
            Transform2D world
    ) {
        if (button.disabled()) {
            return BUTTON_DISABLED_COLOR;
        }

        if (pending) {
            return BUTTON_PENDING_COLOR;
        }

        final var point = world.inverse(mouseX, mouseY);

        return point != null && laidOut.contains(point.x(), point.y())
                && (visible == null || visible.contains(mouseX, mouseY))
                ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
    }

    private static int applyOpacity(int color, float opacity) {
        return (Math.round(((color >>> 24) & 0xFF) * opacity) << 24) | (color & 0x00FFFFFF);
    }

    private static void paintScrollbars(
            LaidOutNode node,
            RenderNode.Scroll scroll,
            float opacity,
            List<DrawCommand> commands
    ) {
        if (node.children().isEmpty()) {
            return;
        }

        final var content = node.children().getFirst();
        final var visible = node.innerBounds();

        if (!scroll.direction().equals("horizontal") && content.height() > visible.height()) {
            final var track = Math.max(1, visible.height());
            final var thumb = Math.min(track, Math.max(8, track * track / content.height()));
            final var range = content.height() - visible.height();
            final var offset = range == 0 ? 0 : (track - thumb) * scroll.offsetY() / range;
            final var x = visible.x() + Math.max(0, visible.width() - SCROLLBAR_THICKNESS);
            commands.add(new DrawCommand.FillRect(
                    x, visible.y(), SCROLLBAR_THICKNESS, track, applyOpacity(SCROLLBAR_TRACK_COLOR, opacity)));
            commands.add(new DrawCommand.FillRect(
                    x, visible.y() + offset, SCROLLBAR_THICKNESS, thumb,
                    applyOpacity(SCROLLBAR_THUMB_COLOR, opacity)));
        }

        if (!scroll.direction().equals("vertical") && content.width() > visible.width()) {
            final var track = Math.max(1, visible.width());
            final var thumb = Math.min(track, Math.max(8, track * track / content.width()));
            final var range = content.width() - visible.width();
            final var offset = range == 0 ? 0 : (track - thumb) * scroll.offsetX() / range;
            final var y = visible.y() + Math.max(0, visible.height() - SCROLLBAR_THICKNESS);
            commands.add(new DrawCommand.FillRect(
                    visible.x(), y, track, SCROLLBAR_THICKNESS, applyOpacity(SCROLLBAR_TRACK_COLOR, opacity)));
            commands.add(new DrawCommand.FillRect(
                    visible.x() + offset, y, thumb, SCROLLBAR_THICKNESS,
                    applyOpacity(SCROLLBAR_THUMB_COLOR, opacity)));
        }
    }

    private static void paintBorder(
            LaidOutNode node,
            RenderNode.VisualStyle style,
            float opacity,
            List<DrawCommand> commands
    ) {
        final var thickness = Math.min(style.borderWidth(), Math.min(node.width(), node.height()) / 2);
        if (thickness <= 0) {
            return;
        }

        final var color = applyOpacity(style.borderColor(), opacity);
        commands.add(new DrawCommand.FillRect(node.x(), node.y(), node.width(), thickness, color));
        commands.add(new DrawCommand.FillRect(
                node.x(), node.y() + node.height() - thickness, node.width(), thickness, color));
        commands.add(new DrawCommand.FillRect(
                node.x(), node.y() + thickness, thickness, node.height() - 2 * thickness, color));
        commands.add(new DrawCommand.FillRect(
                node.x() + node.width() - thickness, node.y() + thickness,
                thickness, node.height() - 2 * thickness, color));
    }
}
