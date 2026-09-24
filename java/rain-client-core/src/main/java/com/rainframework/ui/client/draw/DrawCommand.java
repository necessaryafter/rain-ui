package com.rainframework.ui.client.draw;

import org.jspecify.annotations.Nullable;

/** One thing for the version module to draw, in order; colors are ARGB. */
public sealed interface DrawCommand {

    record FillRect(int x, int y, int width, int height, int color) implements DrawCommand {
    }

    record DrawText(String text, int x, int y, int color, boolean shadow, @Nullable String fontHash)
            implements DrawCommand {
    }

    record DrawItem(String itemData, int x, int y, int size) implements DrawCommand {
    }

    record DrawImage(String hash, int x, int y, int width, int height) implements DrawCommand {
    }
}
