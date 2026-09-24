package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.Alignment;
import com.rainframework.ui.client.render.Justify;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.client.render.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Text is 6 px per character and 9 px tall (TestContracts.MONOSPACE); the screen is 400x300.
class LayoutEngineTest {
    private final LayoutEngine engine = new LayoutEngine(MONOSPACE);

    @Test
    void sizesAColumnToItsContentAndCentersItOnTheScreen() {
        final var root = column(4, 8, text("Loja"), text("Pikachu"));

        final var laidOut = engine.layout(root, 400, 300);

        // width: widest child 42 + 2*8 = 58; height: 9 + 4 + 9 + 2*8 = 38
        assertBounds(laidOut, (400 - 58) / 2, (300 - 38) / 2, 58, 38);
        assertBounds(laidOut.children().get(0), laidOut.x() + 8, laidOut.y() + 8, 24, 9);
        assertBounds(laidOut.children().get(1), laidOut.x() + 8, laidOut.y() + 8 + 9 + 4, 42, 9);
    }

    @Test
    void placesRowChildrenLeftToRightWithTheGap() {
        final var root = row(4, 0, new RenderNode.Item("AA==", 16), text("Pikachu"));

        final var laidOut = engine.layout(root, 400, 300);

        assertBounds(laidOut, laidOut.x(), laidOut.y(), 16 + 4 + 42, 16);
        assertEquals(laidOut.x() + 20, laidOut.children().get(1).x());
    }

    @Test
    void sharesTheLeftoverSpaceBetweenFillChildren() {
        final var fillA = new RenderNode.Box(RenderNode.Direction.COLUMN, 0, 0, Alignment.START, Justify.START,
                Size.FILL, Size.FIT, List.of());
        final var fillB = new RenderNode.Box(RenderNode.Direction.COLUMN, 0, 0, Alignment.START, Justify.START,
                Size.FILL, Size.FIT, List.of());
        final var root = new RenderNode.Box(RenderNode.Direction.ROW, 0, 0, Alignment.START, Justify.START,
                Size.fixed(200), Size.FIT, List.of(text("abc"), fillA, fillB));

        final var laidOut = engine.layout(root, 400, 300);

        // 200 - 18 for the text leaves 182, split in two
        assertEquals(91, laidOut.children().get(1).width());
        assertEquals(91, laidOut.children().get(2).width());
        assertEquals(laidOut.x() + 18 + 91, laidOut.children().get(2).x());
    }

    @Test
    void justifiesAndAlignsInsideAFixedBox() {
        final var root = new RenderNode.Box(RenderNode.Direction.ROW, 0, 0, Alignment.CENTER, Justify.END,
                Size.fixed(100), Size.fixed(40), List.of(text("ab")));

        final var child = engine.layout(root, 400, 300).children().getFirst();

        assertEquals(88, child.x() - (400 - 100) / 2);
        assertEquals((40 - 9) / 2, child.y() - (300 - 40) / 2);
    }

    @Test
    void spacesChildrenApartWithSpaceBetween() {
        final var root = new RenderNode.Box(RenderNode.Direction.ROW, 0, 0, Alignment.START, Justify.SPACE_BETWEEN,
                Size.fixed(100), Size.FIT, List.of(text("a"), text("b"), text("c")));

        final var laidOut = engine.layout(root, 400, 300);

        // 100 - 18 = 82 left, 41 between each pair
        assertEquals(List.of(0, 47, 94), laidOut.children().stream().map(child -> child.x() - laidOut.x()).toList());
    }

    @Test
    void padsButtonContent() {
        final var button = new RenderNode.Button("shop:buy", "{}", false, column(0, 0, text("Comprar")));
        final var root = column(0, 0, button);

        final var laidOutButton = engine.layout(root, 400, 300).children().getFirst();

        assertEquals(42 + 2 * LayoutEngine.BUTTON_PADDING, laidOutButton.width());
        assertEquals(9 + 2 * LayoutEngine.BUTTON_PADDING, laidOutButton.height());
        assertEquals(laidOutButton.x() + LayoutEngine.BUTTON_PADDING, laidOutButton.children().getFirst().x());
    }

    @Test
    void keepsTheImageAspectRatioWhenOneSideIsFixed() {
        final var image = new RenderNode.Image("h", 128, 64, Size.fixed(32), Size.FIT);

        final var laidOut = engine.layout(column(0, 0, image), 400, 300).children().getFirst();

        assertEquals(32, laidOut.width());
        assertEquals(16, laidOut.height());
    }

    @Test
    void fillsTheScreenWhenTheRootAsksForIt() {
        final var root = new RenderNode.Box(RenderNode.Direction.COLUMN, 0, 0, Alignment.START, Justify.START,
                Size.FILL, Size.FILL, List.of());

        assertBounds(engine.layout(root, 400, 300), 0, 0, 400, 300);
    }

    private static RenderNode.Box column(int gap, int padding, RenderNode... children) {
        return new RenderNode.Box(RenderNode.Direction.COLUMN, gap, padding, Alignment.START, Justify.START,
                Size.FIT, Size.FIT, List.of(children));
    }

    private static RenderNode.Box row(int gap, int padding, RenderNode... children) {
        return new RenderNode.Box(RenderNode.Direction.ROW, gap, padding, Alignment.START, Justify.START,
                Size.FIT, Size.FIT, List.of(children));
    }

    private static RenderNode.Text text(String value) {
        return new RenderNode.Text(value, 0xFFFFFFFF, false, null);
    }

    private static void assertBounds(LaidOutNode node, int x, int y, int width, int height) {
        assertEquals(List.of(x, y, width, height), List.of(node.x(), node.y(), node.width(), node.height()));
    }
}
