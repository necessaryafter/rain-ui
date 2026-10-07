package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.RenderNode;

import java.util.List;

/** A node with its final position and size in GUI pixels, relative to the screen. */
public record LaidOutNode(RenderNode node, int x, int y, int width, int height, List<LaidOutNode> children) {

    public Bounds bounds() {
        return new Bounds(x, y, width, height);
    }

    public Bounds innerBounds() {
        final var padding = node.layoutProps().padding();

        return new Bounds(
                x + padding.left(),
                y + padding.top(),
                Math.max(0, width - padding.horizontal()),
                Math.max(0, height - padding.vertical()));
    }

    public boolean contains(double pointX, double pointY) {
        return bounds().contains(pointX, pointY);
    }
}
