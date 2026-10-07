package com.rainframework.ui.client.render;

public final class ShapeRasterizer {
    private static final int SAMPLES_PER_AXIS = 4;
    private static final int SAMPLES_PER_PIXEL = SAMPLES_PER_AXIS * SAMPLES_PER_AXIS;

    private ShapeRasterizer() {
    }

    public static Mask rasterize(
            ShapeGeometry geometry,
            int logicalWidth,
            int logicalHeight,
            int textureWidth,
            int textureHeight,
            int borderWidth
    ) {
        if (logicalWidth <= 0 || logicalHeight <= 0 || textureWidth <= 0 || textureHeight <= 0
                || borderWidth < 0) {
            throw new IllegalArgumentException("Shape dimensions and border width must be valid");
        }

        final var pixels = Math.multiplyExact(textureWidth, textureHeight);
        final var fill = new byte[pixels];
        final var border = new byte[pixels];
        final var resolved = geometry.resolve(logicalWidth, logicalHeight);

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                final var counts = sample(resolved, x, y, logicalWidth, logicalHeight,
                        textureWidth, textureHeight, borderWidth);
                final var index = y * textureWidth + x;
                fill[index] = alpha(counts.fill());
                border[index] = alpha(counts.border());
            }
        }

        return new Mask(textureWidth, textureHeight, fill, border);
    }

    private static Counts sample(
            ShapeGeometry.Resolved shape,
            int x,
            int y,
            int logicalWidth,
            int logicalHeight,
            int textureWidth,
            int textureHeight,
            int borderWidth
    ) {
        var fill = 0;
        var border = 0;

        for (int sampleY = 0; sampleY < SAMPLES_PER_AXIS; sampleY++) {
            for (int sampleX = 0; sampleX < SAMPLES_PER_AXIS; sampleX++) {
                final var pointX = (x + (sampleX + 0.5) / SAMPLES_PER_AXIS)
                        * logicalWidth / textureWidth;
                final var pointY = (y + (sampleY + 0.5) / SAMPLES_PER_AXIS)
                        * logicalHeight / textureHeight;
                final var distance = shape.signedDistance(pointX, pointY);
                if (distance > 0) {
                    continue;
                }

                fill++;
                if (borderWidth > 0 && distance >= -borderWidth) {
                    border++;
                }
            }
        }

        return new Counts(fill, border);
    }

    private static byte alpha(int samples) {
        return (byte) ((samples * 255 + SAMPLES_PER_PIXEL / 2) / SAMPLES_PER_PIXEL);
    }

    public record Mask(int width, int height, byte[] fill, byte[] border) {
    }

    private record Counts(int fill, int border) {
    }
}
