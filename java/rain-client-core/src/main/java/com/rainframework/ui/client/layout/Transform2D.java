package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.RenderNode;
import org.jspecify.annotations.Nullable;

public record Transform2D(double a, double b, double c, double d, double tx, double ty) {
    public static final Transform2D IDENTITY = new Transform2D(1, 0, 0, 1, 0, 0);

    public static Transform2D around(RenderNode.VisualTransform style, Bounds bounds) {
        if (style.isIdentity()) {
            return IDENTITY;
        }

        final var radians = Math.toRadians(style.rotate());
        final var cosine = Math.cos(radians);
        final var sine = Math.sin(radians);
        final var skewX = Math.tan(Math.toRadians(style.skewX()));
        final var skewY = Math.tan(Math.toRadians(style.skewY()));
        final var a = style.scaleX() * (cosine - sine * skewY);
        final var b = style.scaleX() * (sine + cosine * skewY);
        final var c = style.scaleY() * (cosine * skewX - sine);
        final var d = style.scaleY() * (sine * skewX + cosine);
        final var centerX = bounds.x() + bounds.width() / 2.0;
        final var centerY = bounds.y() + bounds.height() / 2.0;

        return new Transform2D(a, b, c, d,
                centerX - a * centerX - c * centerY,
                centerY - b * centerX - d * centerY);
    }

    public Transform2D compose(Transform2D child) {
        if (child == IDENTITY) {
            return this;
        }

        return new Transform2D(
                a * child.a + c * child.b,
                b * child.a + d * child.b,
                a * child.c + c * child.d,
                b * child.c + d * child.d,
                a * child.tx + c * child.ty + tx,
                b * child.tx + d * child.ty + ty);
    }

    public @Nullable Point inverse(double x, double y) {
        final var determinant = a * d - b * c;
        if (determinant == 0 || !Double.isFinite(determinant)) {
            return null;
        }

        final var relativeX = x - tx;
        final var relativeY = y - ty;

        return new Point((d * relativeX - c * relativeY) / determinant,
                (a * relativeY - b * relativeX) / determinant);
    }

    public Point mapPoint(double x, double y) {
        return new Point(mapX(x, y), mapY(x, y));
    }

    public Bounds map(Bounds bounds) {
        final var x0 = bounds.x();
        final var y0 = bounds.y();
        final var x1 = (long) bounds.x() + bounds.width();
        final var y1 = (long) bounds.y() + bounds.height();
        final var left = Math.min(Math.min(mapX(x0, y0), mapX(x1, y0)),
                Math.min(mapX(x0, y1), mapX(x1, y1)));
        final var top = Math.min(Math.min(mapY(x0, y0), mapY(x1, y0)),
                Math.min(mapY(x0, y1), mapY(x1, y1)));
        final var right = Math.max(Math.max(mapX(x0, y0), mapX(x1, y0)),
                Math.max(mapX(x0, y1), mapX(x1, y1)));
        final var bottom = Math.max(Math.max(mapY(x0, y0), mapY(x1, y0)),
                Math.max(mapY(x0, y1), mapY(x1, y1)));
        if (!Double.isFinite(left) || !Double.isFinite(top)
                || !Double.isFinite(right) || !Double.isFinite(bottom)) {
            return new Bounds(0, 0, 0, 0);
        }

        final var mappedLeft = clamp(Math.ceil(left));
        final var mappedTop = clamp(Math.ceil(top));
        final var mappedRight = clamp(Math.floor(right));
        final var mappedBottom = clamp(Math.floor(bottom));

        return new Bounds(mappedLeft, mappedTop,
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) mappedRight - mappedLeft)),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) mappedBottom - mappedTop)));
    }

    private double mapX(double x, double y) {
        return a * x + c * y + tx;
    }

    private double mapY(double x, double y) {
        return b * x + d * y + ty;
    }

    private static int clamp(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    public record Point(double x, double y) {
    }
}
