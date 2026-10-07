package com.rainframework.ui.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3ShapeGeometryTest {
    @Test
    void polygonUsesPercentCoordinatesAndIncludesItsBoundary() {
        final var polygon = new ShapeGeometry.Polygon(List.of(
                new ShapeGeometry.Point(0, 0),
                new ShapeGeometry.Point(10_000, 0),
                new ShapeGeometry.Point(0, 10_000)));
        final var resolved = polygon.resolve(20, 20);

        assertTrue(resolved.contains(2, 2));
        assertTrue(resolved.contains(10, 10));
        assertFalse(resolved.contains(18, 18));
        assertTrue(resolved.signedDistance(5, 5) < 0);
        assertTrue(resolved.signedDistance(18, 18) > 0);
    }

    @Test
    void roundedShapeClampsRadiusToItsSmallestSide() {
        final var rounded = new ShapeGeometry.Rounded(100).resolve(20, 20);

        assertFalse(rounded.contains(0, 0));
        assertTrue(rounded.contains(10, 0));
        assertTrue(rounded.contains(10, 10));
        assertFalse(rounded.contains(19, 19));
    }
}
