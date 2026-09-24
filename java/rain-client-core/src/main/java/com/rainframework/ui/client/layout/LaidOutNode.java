package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.RenderNode;

import java.util.List;

/** A node with its final position and size in GUI pixels, relative to the screen. */
public record LaidOutNode(RenderNode node, int x, int y, int width, int height, List<LaidOutNode> children) {

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
