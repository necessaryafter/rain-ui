package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.RenderNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class M3TransformTest {
    @Test
    void appliesScaleBeforeSkewAndRotationAroundTheBorderBoxCenter() {
        final var bounds = new Bounds(10, 20, 20, 20);
        final var style = new RenderNode.VisualTransform(90, 2, 1, 0, 0);
        final var transform = Transform2D.around(style, bounds);
        final var mapped = transform.mapPoint(25, 30);
        final var restored = transform.inverse(mapped.x(), mapped.y());

        assertEquals(20, mapped.x(), 0.00001);
        assertEquals(40, mapped.y(), 0.00001);
        assertNotNull(restored);
        assertEquals(25, restored.x(), 0.00001);
        assertEquals(30, restored.y(), 0.00001);
    }

    @Test
    void composesParentAndChildTransformsAndMapsAxisAlignedClips() {
        final var parent = Transform2D.around(
                new RenderNode.VisualTransform(0, 2, 2, 0, 0),
                new Bounds(10, 20, 20, 20));
        final var child = Transform2D.around(
                new RenderNode.VisualTransform(0, 0.5, 0.5, 0, 0),
                new Bounds(15, 25, 10, 10));
        final var composed = parent.compose(child);
        final var childPoint = child.mapPoint(22, 31);
        final var expected = parent.mapPoint(childPoint.x(), childPoint.y());

        assertEquals(expected.x(), composed.mapPoint(22, 31).x(), 0.00001);
        assertEquals(expected.y(), composed.mapPoint(22, 31).y(), 0.00001);
        assertEquals(new Bounds(0, 10, 40, 40), parent.map(new Bounds(10, 20, 20, 20)));
    }

    @Test
    void skewRunsBetweenScaleAndRotation() {
        final var transform = Transform2D.around(
                new RenderNode.VisualTransform(90, 2, 3, 45, 0),
                new Bounds(10, 20, 20, 20));
        final var mapped = transform.mapPoint(21, 31);

        assertEquals(17, mapped.x(), 0.00001);
        assertEquals(35, mapped.y(), 0.00001);
    }
}
