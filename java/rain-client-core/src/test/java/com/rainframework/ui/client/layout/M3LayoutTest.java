package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import com.rainframework.ui.client.render.Alignment;
import com.rainframework.ui.client.render.Justify;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.client.render.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3LayoutTest {
    private final LayoutEngine engine = new LayoutEngine(MONOSPACE);

    @Test
    void stacksChildrenInTheSameAvailableArea() {
        final var first = text("first");
        final var second = text("second");
        final var stack = new RenderNode.Box(
                RenderNode.Direction.STACK, 0, 4, Alignment.START, Justify.START,
                Size.fixed(100), Size.fixed(40), List.of(first, second));

        final var laidOut = engine.layout(stack, 200, 100);

        assertEquals(laidOut.children().get(0).x(), laidOut.children().get(1).x());
        assertEquals(laidOut.children().get(0).y(), laidOut.children().get(1).y());
        assertEquals(laidOut.x() + 4, laidOut.children().get(0).x());
    }

    @Test
    void placesGridChildrenInUniformCells() {
        final var grid = new RenderNode.Grid(
                20, 10, 3, 2, 2, Size.FIT, Size.FIT,
                List.of(text("a"), text("b"), text("c")));
        final var root = box(List.of(grid), RenderNode.VisualStyle.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100).children().getFirst();

        assertEquals(47, laidOut.width());
        assertEquals(27, laidOut.height());
        assertEquals(laidOut.x() + 25, laidOut.children().get(1).x());
        assertEquals(laidOut.y() + 15, laidOut.children().get(2).y());
    }

    @Test
    void paintsStackByZIndexAndClipsChildren() {
        final var back = box(List.of(), new RenderNode.VisualStyle(0xFFFF0000, 1, 0, 0, false, 1));
        final var front = box(List.of(), new RenderNode.VisualStyle(0xFF0000FF, 1, 0, 0, false, 2));
        final var root = new RenderNode.Box(
                RenderNode.Direction.STACK, 0, 0, Alignment.START, Justify.START,
                Size.fixed(30), Size.fixed(20), List.of(front, back),
                new RenderNode.VisualStyle(0, 1, 0, 0, true, 0));

        final var commands = new Painter().paint(engine.layout(root, 100, 100), -1, -1, false);
        final var clip = commands.stream().filter(DrawCommand.PushClip.class::isInstance).findFirst();
        final var red = commands.stream().filter(command -> command instanceof DrawCommand.FillRect rect
                && rect.color() == 0xFFFF0000).findFirst();
        final var blue = commands.stream().filter(command -> command instanceof DrawCommand.FillRect rect
                && rect.color() == 0xFF0000FF).findFirst();

        assertTrue(clip.isPresent());
        assertTrue(red.isPresent());
        assertTrue(blue.isPresent());
        assertTrue(commands.indexOf(red.orElseThrow()) < commands.indexOf(blue.orElseThrow()));
        assertTrue(commands.getLast() instanceof DrawCommand.PopClip);
    }

    private static RenderNode.Box box(List<RenderNode> children, RenderNode.VisualStyle style) {
        return new RenderNode.Box(
                RenderNode.Direction.COLUMN, 0, 0, Alignment.START, Justify.START,
                Size.FIT, Size.FIT, children, style);
    }

    private static RenderNode.Text text(String value) {
        return new RenderNode.Text(value, 0xFFFFFFFF, false, null);
    }
}
