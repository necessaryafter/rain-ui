package com.rainframework.ui.client.render;

import java.util.List;

public sealed interface ShapeGeometry permits ShapeGeometry.Polygon, ShapeGeometry.Rounded {
    Resolved resolve(int width, int height);

    interface Resolved {
        double signedDistance(double x, double y);

        default boolean contains(double x, double y) {
            return signedDistance(x, y) <= 0;
        }
    }

    record Point(int x, int y) {
    }

    record Polygon(List<Point> points) implements ShapeGeometry {
        public Polygon {
            points = List.copyOf(points);
        }

        @Override
        public Resolved resolve(int width, int height) {
            final var x = new double[points.size()];
            final var y = new double[points.size()];

            for (int i = 0; i < points.size(); i++) {
                x[i] = (double) points.get(i).x() * width / 10_000;
                y[i] = (double) points.get(i).y() * height / 10_000;
            }

            return new ResolvedPolygon(x, y);
        }
    }

    record Rounded(int radius) implements ShapeGeometry {
        @Override
        public Resolved resolve(int width, int height) {
            final var clampedRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0));

            return (x, y) -> {
                final var horizontal = Math.abs(x - width / 2.0) - (width / 2.0 - clampedRadius);
                final var vertical = Math.abs(y - height / 2.0) - (height / 2.0 - clampedRadius);
                final var outside = Math.hypot(Math.max(0, horizontal), Math.max(0, vertical));
                final var inside = Math.min(Math.max(horizontal, vertical), 0);

                return outside + inside - clampedRadius;
            };
        }
    }

    final class ResolvedPolygon implements Resolved {
        private final double[] x;
        private final double[] y;

        private ResolvedPolygon(double[] x, double[] y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public double signedDistance(double pointX, double pointY) {
            var inside = false;
            var minimumSquared = Double.POSITIVE_INFINITY;

            for (int i = 0; i < x.length; i++) {
                final var next = (i + 1) % x.length;
                final var dx = x[next] - x[i];
                final var dy = y[next] - y[i];
                final var lengthSquared = dx * dx + dy * dy;
                final var projection = lengthSquared == 0 ? 0
                        : Math.max(0, Math.min(1, ((pointX - x[i]) * dx + (pointY - y[i]) * dy)
                        / lengthSquared));
                final var nearestX = x[i] + projection * dx;
                final var nearestY = y[i] + projection * dy;
                final var distanceX = pointX - nearestX;
                final var distanceY = pointY - nearestY;
                minimumSquared = Math.min(minimumSquared, distanceX * distanceX + distanceY * distanceY);

                if ((y[i] > pointY) != (y[next] > pointY)
                        && pointX < x[i] + (pointY - y[i]) * dx / dy) {
                    inside = !inside;
                }
            }

            final var distance = Math.sqrt(minimumSquared);

            return inside ? -distance : distance;
        }
    }
}
