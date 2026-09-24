package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.Alignment;
import com.rainframework.ui.client.render.Justify;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.client.render.Size;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A small row/column layout: children take their fixed or content size on the main axis, {@code fill} children share
 * what is left, and {@code justify} places the rest only when nothing fills. The root is centered on the screen.
 */
@RequiredArgsConstructor
public final class LayoutEngine {
    public static final int BUTTON_PADDING = 4;

    private final Measurer measurer;

    public LaidOutNode layout(RenderNode.Box root, int screenWidth, int screenHeight) {
        final var width = resolve(root.width(), measureWidth(root), screenWidth);
        final var height = resolve(root.height(), measureHeight(root), screenHeight);

        return place(root, (screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    private static int resolve(Size size, int content, int available) {
        return switch (size.kind()) {
            case FIXED -> size.pixels();
            case FIT -> content;
            case FILL -> available;
        };
    }

    int measureWidth(RenderNode node) {
        return measure(node, true);
    }

    int measureHeight(RenderNode node) {
        return measure(node, false);
    }

    // The content size on one axis; a fixed size wins over the content.
    private int measure(RenderNode node, boolean horizontal) {
        final var declared = horizontal ? node.width() : node.height();
        if (declared.isFixed()) {
            return declared.pixels();
        }

        return switch (node) {
            case RenderNode.Text text -> horizontal
                    ? measurer.textWidth(text.value(), text.fontHash())
                    : measurer.lineHeight(text.fontHash());
            case RenderNode.Item item -> item.size();
            case RenderNode.Image image -> imageSize(image, horizontal);
            case RenderNode.Button button -> measure(button.content(), horizontal) + 2 * BUTTON_PADDING;
            case RenderNode.Box box -> measureBox(box, horizontal);
        };
    }

    // With one side fixed and the other fit, the image keeps its aspect ratio.
    private static int imageSize(RenderNode.Image image, boolean horizontal) {
        if (horizontal && image.height().isFixed() && image.intrinsicHeight() > 0) {
            return Math.round((float) image.intrinsicWidth() * image.height().pixels() / image.intrinsicHeight());
        }

        if (!horizontal && image.width().isFixed() && image.intrinsicWidth() > 0) {
            return Math.round((float) image.intrinsicHeight() * image.width().pixels() / image.intrinsicWidth());
        }

        return horizontal ? image.intrinsicWidth() : image.intrinsicHeight();
    }

    private int measureBox(RenderNode.Box box, boolean horizontal) {
        final var alongMainAxis = horizontal == (box.direction() == RenderNode.Direction.ROW);
        var total = 0;

        for (final var child : box.children()) {
            final var size = measure(child, horizontal);
            total = alongMainAxis ? total + size : Math.max(total, size);
        }

        if (alongMainAxis && !box.children().isEmpty()) {
            total += box.gap() * (box.children().size() - 1);
        }

        return total + 2 * box.padding();
    }

    private LaidOutNode place(RenderNode node, int x, int y, int width, int height) {
        return switch (node) {
            case RenderNode.Box box -> placeBox(box, x, y, width, height);
            case RenderNode.Button button -> new LaidOutNode(button, x, y, width, height, List.of(placeBox(
                    button.content(),
                    x + BUTTON_PADDING,
                    y + BUTTON_PADDING,
                    Math.max(0, width - 2 * BUTTON_PADDING),
                    Math.max(0, height - 2 * BUTTON_PADDING))));
            default -> new LaidOutNode(node, x, y, width, height, List.of());
        };
    }

    private LaidOutNode placeBox(RenderNode.Box box, int x, int y, int width, int height) {
        final var row = box.direction() == RenderNode.Direction.ROW;
        final var innerMain = Math.max(0, (row ? width : height) - 2 * box.padding());
        final var innerCross = Math.max(0, (row ? height : width) - 2 * box.padding());
        final var children = box.children();

        final var mainSizes = new int[children.size()];
        var used = box.gap() * Math.max(0, children.size() - 1);
        var fillCount = 0;

        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            if (mainSize(child, row).isFill()) {
                fillCount++;
                continue;
            }

            mainSizes[i] = measure(child, row);
            used += mainSizes[i];
        }

        final var leftover = Math.max(0, innerMain - used);
        if (fillCount > 0) {
            for (int i = 0; i < children.size(); i++) {
                if (mainSize(children.get(i), row).isFill()) {
                    mainSizes[i] = leftover / fillCount;
                }
            }
        }

        var offset = fillCount > 0 ? 0 : startOffset(box.justify(), leftover);
        final var spacing = box.gap() + (fillCount == 0 ? betweenSpacing(box.justify(), leftover, children.size()) : 0);

        final var placed = new ArrayList<LaidOutNode>();
        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            final var cross = crossSize(child, row, innerCross);
            final var crossOffset = crossOffset(box.align(), innerCross - cross);
            final var main = offset + box.padding();

            placed.add(row
                    ? place(child, x + main, y + box.padding() + crossOffset, mainSizes[i], cross)
                    : place(child, x + box.padding() + crossOffset, y + main, cross, mainSizes[i]));

            offset += mainSizes[i] + spacing;
        }

        return new LaidOutNode(box, x, y, width, height, placed);
    }

    private static Size mainSize(RenderNode child, boolean row) {
        return row ? child.width() : child.height();
    }

    private int crossSize(RenderNode child, boolean row, int innerCross) {
        final var declared = row ? child.height() : child.width();
        if (declared.isFill()) {
            return innerCross;
        }

        return measure(child, !row);
    }

    private static int startOffset(Justify justify, int leftover) {
        return switch (justify) {
            case CENTER -> leftover / 2;
            case END -> leftover;
            default -> 0;
        };
    }

    private static int betweenSpacing(Justify justify, int leftover, int count) {
        if (justify != Justify.SPACE_BETWEEN || count < 2) {
            return 0;
        }

        return leftover / (count - 1);
    }

    private static int crossOffset(Alignment align, int free) {
        return switch (align) {
            case CENTER -> Math.max(0, free) / 2;
            case END -> Math.max(0, free);
            case START -> 0;
        };
    }
}
