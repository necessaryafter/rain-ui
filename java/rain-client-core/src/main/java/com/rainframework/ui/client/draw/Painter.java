package com.rainframework.ui.client.draw;

import com.rainframework.ui.client.layout.LaidOutNode;
import com.rainframework.ui.client.render.RenderNode;

import java.util.ArrayList;
import java.util.List;

/** Turns a laid-out screen into draw commands, including the panel behind it and the button states. */
public final class Painter {
    public static final int PANEL_COLOR = 0xC0101010;
    public static final int BUTTON_COLOR = 0xFF3A3A3A;
    public static final int BUTTON_HOVER_COLOR = 0xFF505050;
    public static final int BUTTON_DISABLED_COLOR = 0xFF202020;
    public static final int BUTTON_PENDING_COLOR = 0xFF2A2A48;
    public static final int DISABLED_TEXT_COLOR = 0xFF808080;

    public List<DrawCommand> paint(LaidOutNode root, double mouseX, double mouseY, boolean pending) {
        final var commands = new ArrayList<DrawCommand>();
        commands.add(new DrawCommand.FillRect(root.x(), root.y(), root.width(), root.height(), PANEL_COLOR));

        paintNode(root, mouseX, mouseY, pending, false, commands);
        return commands;
    }

    private void paintNode(
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            boolean dimmed,
            List<DrawCommand> commands
    ) {
        switch (laidOut.node()) {
            case RenderNode.Text text -> commands.add(new DrawCommand.DrawText(
                    text.value(),
                    laidOut.x(),
                    laidOut.y(),
                    dimmed ? DISABLED_TEXT_COLOR : text.color(),
                    text.shadow(),
                    text.fontHash()));
            case RenderNode.Item item -> commands.add(new DrawCommand.DrawItem(
                    item.itemData(),
                    laidOut.x(),
                    laidOut.y(),
                    item.size()));
            case RenderNode.Image image -> commands.add(new DrawCommand.DrawImage(
                    image.hash(),
                    laidOut.x(),
                    laidOut.y(),
                    laidOut.width(),
                    laidOut.height()));
            case RenderNode.Button button -> {
                final var color = buttonColor(button, laidOut, mouseX, mouseY, pending);
                commands.add(new DrawCommand.FillRect(
                        laidOut.x(),
                        laidOut.y(),
                        laidOut.width(),
                        laidOut.height(),
                        color));
                paintChildren(laidOut, mouseX, mouseY, pending, dimmed || button.disabled(), commands);
            }
            case RenderNode.Box ignored -> paintChildren(laidOut, mouseX, mouseY, pending, dimmed, commands);
        }
    }

    private void paintChildren(
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending,
            boolean dimmed,
            List<DrawCommand> commands
    ) {
        for (final var child : laidOut.children()) {
            paintNode(child, mouseX, mouseY, pending, dimmed, commands);
        }
    }

    // While an interaction is pending every button shows it, since none of them accept clicks until the reply.
    private static int buttonColor(
            RenderNode.Button button,
            LaidOutNode laidOut,
            double mouseX,
            double mouseY,
            boolean pending
    ) {
        if (button.disabled()) {
            return BUTTON_DISABLED_COLOR;
        }

        if (pending) {
            return BUTTON_PENDING_COLOR;
        }

        return laidOut.contains(mouseX, mouseY) ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
    }
}
