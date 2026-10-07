package com.rainframework.ui.client.layout;

public record Bounds(int x, int y, int width, int height) {
    public boolean contains(double pointX, double pointY) {
        return width > 0 && height > 0
                && pointX >= x && pointX < (long) x + width
                && pointY >= y && pointY < (long) y + height;
    }

    public boolean hasArea() {
        return width > 0 && height > 0;
    }

    public Bounds intersect(Bounds other) {
        final var left = Math.max(x, other.x);
        final var top = Math.max(y, other.y);
        final var right = Math.min((long) x + width, (long) other.x + other.width);
        final var bottom = Math.min((long) y + height, (long) other.y + other.height);

        return new Bounds(left, top, (int) Math.max(0, right - left), (int) Math.max(0, bottom - top));
    }
}
