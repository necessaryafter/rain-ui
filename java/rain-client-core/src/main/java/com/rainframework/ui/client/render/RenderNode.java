package com.rainframework.ui.client.render;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The screen as the client draws it: every binding resolved, every default applied, and {@code show}, {@code match}
 * and {@code list} already replaced by the children they produce.
 */
public sealed interface RenderNode {

    Size width();

    Size height();

    record Box(
            Direction direction,
            int gap,
            int padding,
            Alignment align,
            Justify justify,
            Size width,
            Size height,
            List<RenderNode> children
    ) implements RenderNode {
    }

    record Text(String value, int color, boolean shadow, @Nullable String fontHash) implements RenderNode {

        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.FIT;
        }
    }

    /** {@code itemData} is the base64 of the vanilla network ItemStack codec, decoded by the version module. */
    record Item(String itemData, int size) implements RenderNode {

        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.FIT;
        }
    }

    record Image(String hash, int intrinsicWidth, int intrinsicHeight, Size width, Size height) implements RenderNode {
    }

    /** {@code payloadJson} already has every binding replaced by its value. */
    record Button(String actionId, String payloadJson, boolean disabled, Box content) implements RenderNode {

        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.FIT;
        }
    }

    enum Direction {
        ROW,
        COLUMN
    }
}
