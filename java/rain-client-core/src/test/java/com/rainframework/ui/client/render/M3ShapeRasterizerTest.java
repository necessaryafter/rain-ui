package com.rainframework.ui.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3ShapeRasterizerTest {
    @Test
    void polygonBorderRemainsInsideItsFilledRegion() {
        final var polygon = new ShapeGeometry.Polygon(List.of(
                new ShapeGeometry.Point(0, 0),
                new ShapeGeometry.Point(10_000, 0),
                new ShapeGeometry.Point(0, 10_000)));
        final var mask = ShapeRasterizer.rasterize(polygon, 20, 20, 20, 20, 2);

        assertEquals(255, alpha(mask.fill(), 20, 2, 2));
        assertEquals(0, alpha(mask.border(), 20, 2, 2));
        assertEquals(255, alpha(mask.border(), 20, 9, 9));
        assertEquals(0, alpha(mask.fill(), 20, 18, 18));
    }

    @Test
    void roundedMaskAntialiasesItsOuterEdgeAndKeepsAnInterior() {
        final var mask = ShapeRasterizer.rasterize(
                new ShapeGeometry.Rounded(5), 20, 20, 20, 20, 2);

        assertEquals(0, alpha(mask.fill(), 20, 0, 0));
        assertEquals(255, alpha(mask.fill(), 20, 10, 10));
        assertEquals(0, alpha(mask.border(), 20, 10, 10));
        assertEquals(255, alpha(mask.border(), 20, 10, 0));
        assertTrue(alpha(mask.fill(), 20, 1, 1) > 0);
        assertTrue(alpha(mask.fill(), 20, 1, 1) < 255);
    }

    private static int alpha(byte[] pixels, int width, int x, int y) {
        return pixels[y * width + x] & 0xFF;
    }
}
