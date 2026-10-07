package com.rainframework.ui.client.draw;

import com.rainframework.ui.client.layout.Transform2D;
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

    record DrawImage(String hash, int x, int y, int width, int height, float opacity) implements DrawCommand {
        public DrawImage(String hash, int x, int y, int width, int height) {
            this(hash, x, y, width, height, 1.0F);
        }
    }

    record PushTransform(Transform2D transform) implements DrawCommand {
    }

    record PopTransform() implements DrawCommand {
    }

    record PushClip(int x, int y, int width, int height) implements DrawCommand {
    }

    record PopClip() implements DrawCommand {
    }
}
