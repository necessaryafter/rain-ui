package com.rainframework.ui.client.render;

/**
 * A width or height: a fixed number of GUI pixels, the content's own size ({@code fit}), or the space left
 * ({@code fill}).
 */
public record Size(Kind kind, int pixels) {
    public static final Size FIT = new Size(Kind.FIT, 0);
    public static final Size FILL = new Size(Kind.FILL, 0);

    public enum Kind {
        FIXED,
        FIT,
        FILL
    }

    public static Size fixed(int pixels) {
        return new Size(Kind.FIXED, Math.max(0, pixels));
    }

    public boolean isFixed() {
        return kind == Kind.FIXED;
    }

    public boolean isFill() {
        return kind == Kind.FILL;
    }
}
