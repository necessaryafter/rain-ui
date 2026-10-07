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

class M3FlowLayoutTest {
    private final LayoutEngine engine = new LayoutEngine(MONOSPACE);

    @Test
    void measuresAndPlacesAsymmetricPaddingAndMargins() {
        final var child = box(Size.fixed(20), Size.fixed(10), List.of(),
                props(RenderNode.Insets.ZERO, new RenderNode.Insets(2, 3, 4, 5), false,
                        null, null, null, null, 0, 0), RenderNode.SizeBounds.DEFAULT);
        final var root = box(Size.FIT, Size.FIT, List.of(child),
                props(new RenderNode.Insets(7, 11, 13, 17), RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 0), RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100);
        final var placed = laidOut.children().getFirst();

        assertEquals(56, laidOut.width());
        assertEquals(36, laidOut.height());
        assertEquals(laidOut.x() + 17 + 5, placed.x());
        assertEquals(laidOut.y() + 7 + 2, placed.y());
    }

    @Test
    void absoluteChildrenDoNotUseFlowSpaceAndAnchorToTheDirectInnerBox() {
        final var absolute = box(Size.fixed(20), Size.fixed(10), List.of(),
                props(RenderNode.Insets.ZERO, new RenderNode.Insets(1, 4, 2, 3), true,
                        null, 6, null, 5, 0, 0), RenderNode.SizeBounds.DEFAULT);
        final var root = box(Size.fixed(100), Size.fixed(50), List.of(text("a"), absolute),
                props(RenderNode.Insets.uniform(8), RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 0), RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100);
        final var placed = laidOut.children().get(1);

        assertEquals(laidOut.x() + 100 - 8 - 4 - 6 - 20, placed.x());
        assertEquals(laidOut.y() + 50 - 8 - 2 - 5 - 10, placed.y());
        assertEquals(laidOut.y() + 8, laidOut.children().getFirst().y());

        final var fit = box(Size.FIT, Size.FIT, List.of(text("a"), absolute),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);
        assertEquals(6, engine.layout(fit, 200, 100).width());
    }

    @Test
    void relativeOffsetsMoveDrawingWithoutMovingTheNextFlowSlot() {
        final var shifted = box(Size.fixed(10), Size.fixed(10), List.of(),
                props(RenderNode.Insets.ZERO, RenderNode.Insets.ZERO, false,
                        7, null, -3, null, 0, 0), RenderNode.SizeBounds.DEFAULT);
        final var next = box(Size.fixed(10), Size.fixed(10), List.of(),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);
        final var root = row(50, List.of(shifted, next));

        final var laidOut = engine.layout(root, 100, 100);

        assertEquals(laidOut.x() + 7, laidOut.children().get(0).x());
        assertEquals(laidOut.y() - 3, laidOut.children().get(0).y());
        assertEquals(laidOut.x() + 10, laidOut.children().get(1).x());
    }

    @Test
    void growUsesSourceOrderForRemaindersAndRespectsMaximums() {
        final var first = box(Size.FILL, Size.fixed(10), List.of(),
                RenderNode.LayoutProps.DEFAULT, new RenderNode.SizeBounds(0, 0, 20, Integer.MAX_VALUE));
        final var second = box(Size.FILL, Size.fixed(10), List.of(),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);
        final var root = row(101, List.of(first, second));

        final var laidOut = engine.layout(root, 200, 100);

        assertEquals(20, laidOut.children().get(0).width());
        assertEquals(81, laidOut.children().get(1).width());

        final var equal = engine.layout(row(101, List.of(
                box(Size.FILL, Size.fixed(10), List.of(), RenderNode.LayoutProps.DEFAULT,
                        RenderNode.SizeBounds.DEFAULT),
                box(Size.FILL, Size.fixed(10), List.of(), RenderNode.LayoutProps.DEFAULT,
                        RenderNode.SizeBounds.DEFAULT))), 200, 100);
        assertEquals(51, equal.children().get(0).width());
        assertEquals(50, equal.children().get(1).width());
    }

    @Test
    void shrinkStopsAtEachChildsMinimum() {
        final var first = box(Size.fixed(20), Size.fixed(10), List.of(),
                props(RenderNode.Insets.ZERO, RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 1), new RenderNode.SizeBounds(18, 0, Integer.MAX_VALUE,
                        Integer.MAX_VALUE));
        final var second = box(Size.fixed(20), Size.fixed(10), List.of(),
                props(RenderNode.Insets.ZERO, RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 1), RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(row(30, List.of(first, second)), 100, 100);

        assertEquals(18, laidOut.children().get(0).width());
        assertEquals(12, laidOut.children().get(1).width());
    }

    @Test
    void automaticGridColumnsUseInnerWidthAndIgnoreAbsoluteChildren() {
        final var absolute = box(Size.fixed(5), Size.fixed(5), List.of(),
                props(RenderNode.Insets.ZERO, RenderNode.Insets.ZERO, true,
                        null, 0, null, 0, 0, 0), RenderNode.SizeBounds.DEFAULT);
        final var grid = new RenderNode.Grid(
                20, 10, 3, 0, 0, Size.fixed(51), Size.FIT,
                List.of(text("a"), absolute, text("b"), text("c")), 0,
                props(new RenderNode.Insets(4, 3, 5, 2), RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 0), Alignment.START, Justify.START);
        final var root = box(Size.FIT, Size.FIT, List.of(grid),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100).children().getFirst();

        assertEquals(32, laidOut.height());
        assertEquals(laidOut.x() + 2 + 23, laidOut.children().get(2).x());
        assertEquals(laidOut.y() + 4 + 13, laidOut.children().get(3).y());
        assertEquals(laidOut.x() + 51 - 3 - 5, laidOut.children().get(1).x());
    }

    @Test
    void fillWidthGridMeasuresFitHeightFromAvailableColumns() {
        final var grid = new RenderNode.Grid(20, 10, 2, 2, 0, Size.FILL, Size.FIT,
                List.of(text("a"), text("b"), text("c"), text("d"), text("e")));
        final var root = box(Size.fixed(100), Size.FIT, List.of(grid),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100);
        final var cells = laidOut.children().getFirst();

        assertEquals(26, laidOut.height());
        assertEquals(26, cells.height());
        assertEquals(cells.children().getFirst().y() + 12, cells.children().get(4).y());
    }

    @Test
    void scrollContentUsesViewportWidthForAutomaticGridColumns() {
        final var grid = new RenderNode.Grid(20, 10, 2, 2, 0, Size.FILL, Size.FIT,
                List.of(text("a"), text("b"), text("c"), text("d"), text("e")));
        final var content = box(Size.FIT, Size.FIT, List.of(grid),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);
        final var scroll = new RenderNode.Scroll("scroll", "both", 0, 0,
                Size.fixed(100), Size.fixed(15), content);
        final var root = box(Size.FIT, Size.FIT, List.of(scroll),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);

        final var viewport = engine.layout(root, 200, 100).children().getFirst();
        final var laidOutContent = viewport.children().getFirst();

        assertEquals(100, laidOutContent.width());
        assertEquals(26, laidOutContent.height());
        assertEquals(26, laidOutContent.children().getFirst().height());
    }

    @Test
    void gridDimensionsSaturateInsteadOfOverflowing() {
        final var grid = new RenderNode.Grid(
                Integer.MAX_VALUE, 10, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
                Size.FIT, Size.FIT, List.of(text("a")));
        final var automatic = new RenderNode.Grid(
                Integer.MAX_VALUE, 10, Integer.MAX_VALUE, 0, 0,
                Size.fixed(Integer.MAX_VALUE), Size.FIT, List.of(text("a")));

        assertEquals(Integer.MAX_VALUE, engine.measureWidth(grid));
        assertEquals(10, engine.measureHeight(automatic));
        assertEquals(Integer.MAX_VALUE, new RenderNode.Insets(
                0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE).horizontal());
    }

    @Test
    void absoluteRootUsesTheScreenViewport() {
        final var root = box(Size.fixed(30), Size.fixed(20), List.of(),
                props(RenderNode.Insets.ZERO, RenderNode.Insets.ZERO, true,
                        null, 7, 11, null, 0, 0), RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100);

        assertEquals(200 - 7 - 30, laidOut.x());
        assertEquals(11, laidOut.y());
    }

    @Test
    void scrollContentAndClipUseThePaddedInnerViewport() {
        final var content = box(Size.fixed(100), Size.fixed(100), List.of(),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);
        final var scroll = new RenderNode.Scroll("scroll", "both", 7, 11,
                Size.fixed(50), Size.fixed(40), content, 0,
                props(new RenderNode.Insets(4, 3, 5, 2), RenderNode.Insets.ZERO, false,
                        null, null, null, null, 0, 0));
        final var root = box(Size.FIT, Size.FIT, List.of(scroll),
                RenderNode.LayoutProps.DEFAULT, RenderNode.SizeBounds.DEFAULT);

        final var laidOut = engine.layout(root, 200, 100);
        final var viewport = laidOut.children().getFirst();
        final var clip = viewport.innerBounds();

        assertEquals(new Bounds(viewport.x() + 2, viewport.y() + 4, 45, 31), clip);
        assertEquals(clip.x() - 7, viewport.children().getFirst().x());
        assertEquals(clip.y() - 11, viewport.children().getFirst().y());
        assertEquals(new DrawCommand.PushClip(clip.x(), clip.y(), clip.width(), clip.height()),
                new Painter().paint(laidOut, -1, -1, false).stream()
                        .filter(DrawCommand.PushClip.class::isInstance)
                        .findFirst()
                        .orElseThrow());
    }

    private static RenderNode.Box row(int width, List<RenderNode> children) {
        return new RenderNode.Box(RenderNode.Direction.ROW, 0, 0, Alignment.START, Justify.START,
                Size.fixed(width), Size.FIT, children);
    }

    private static RenderNode.Box box(
            Size width,
            Size height,
            List<RenderNode> children,
            RenderNode.LayoutProps layout,
            RenderNode.SizeBounds bounds
    ) {
        return new RenderNode.Box(RenderNode.Direction.COLUMN, 0, 0, Alignment.START, Justify.START,
                width, height, children, RenderNode.VisualStyle.DEFAULT, bounds, layout);
    }

    private static RenderNode.LayoutProps props(
            RenderNode.Insets padding,
            RenderNode.Insets margin,
            boolean absolute,
            Integer left,
            Integer right,
            Integer top,
            Integer bottom,
            double grow,
            double shrink
    ) {
        return new RenderNode.LayoutProps(
                padding, margin, absolute, left, right, top, bottom, grow, shrink, RenderNode.SizeBounds.DEFAULT);
    }

    private static RenderNode.Text text(String value) {
        return new RenderNode.Text(value, 0xFFFFFFFF, false, null);
    }
}
