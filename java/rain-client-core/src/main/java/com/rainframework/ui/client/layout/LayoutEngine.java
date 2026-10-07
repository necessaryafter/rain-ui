package com.rainframework.ui.client.layout;

import com.rainframework.ui.client.render.Alignment;
import com.rainframework.ui.client.render.Justify;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.client.render.Size;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class LayoutEngine {
    public static final int BUTTON_PADDING = 4;

    private final Measurer measurer;

    public LaidOutNode layout(RenderNode.Box root, int screenWidth, int screenHeight) {
        if (root.layoutProps().absolute()) {
            return placeAbsolute(root, 0, 0, screenWidth, screenHeight);
        }

        final var width = root.bounds().clamp(resolve(root.width(), measureWidth(root), screenWidth), true);
        final var height = root.bounds().clamp(
                resolve(root.height(), measureHeightForWidth(root, width), screenHeight), false);

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

    private int measureHeightForWidth(RenderNode node, int width) {
        if (node.height().isFixed()) {
            return clamp(node, node.height().pixels(), false);
        }

        final var content = switch (node) {
            case RenderNode.Grid grid -> gridSize(grid, false, width);
            case RenderNode.Box box -> measureBoxHeight(box, width);
            case RenderNode.Scroll scroll -> measureHeightForWidth(scroll.content(),
                    scrollContentWidth(scroll, width)) + scroll.layoutProps().padding().vertical();
            case RenderNode.Button button -> measureHeightForWidth(button.content(),
                    Math.max(0, width - 2 * BUTTON_PADDING)) + 2 * BUTTON_PADDING;
            case RenderNode.Tabs tabs -> 20 + measureHeightForWidth(tabs.content(), width);
            default -> measureHeight(node);
        };

        return clamp(node, content, false);
    }

    private int measure(RenderNode node, boolean horizontal) {
        final var declared = horizontal ? node.width() : node.height();
        if (declared.isFixed()) {
            return clamp(node, declared.pixels(), horizontal);
        }

        final var content = switch (node) {
            case RenderNode.Text text -> horizontal
                    ? measurer.textWidth(text.value(), text.fontHash())
                    : measurer.lineHeight(text.fontHash());
            case RenderNode.Item item -> item.size();
            case RenderNode.Image image -> imageSize(image, horizontal);
            case RenderNode.Input input -> horizontal
                    ? Math.max(40, measurer.textWidth(
                            input.value().isEmpty() ? input.placeholder() : input.value(), null))
                    : (input.multiline() ? 72 : 20);
            case RenderNode.Grid grid -> gridSize(grid, horizontal);
            case RenderNode.Scroll scroll -> measure(scroll.content(), horizontal)
                    + (horizontal ? scroll.layoutProps().padding().horizontal()
                    : scroll.layoutProps().padding().vertical());
            case RenderNode.Tabs tabs -> measureTabs(tabs, horizontal);
            case RenderNode.TabHeader header -> horizontal ? measurer.textWidth(header.label(), null) + 12 : 20;
            case RenderNode.Button button -> measure(button.content(), horizontal) + 2 * BUTTON_PADDING;
            case RenderNode.Box box -> measureBox(box, horizontal);
        };

        return clamp(node, content, horizontal);
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
        final var padding = box.layoutProps().padding();
        if (box.direction() == RenderNode.Direction.STACK) {
            int largest = 0;

            for (final var child : box.children()) {
                if (!child.layoutProps().absolute()) {
                    largest = Math.max(largest, measure(child, horizontal) + marginSize(child, horizontal));
                }
            }

            return largest + (horizontal ? padding.horizontal() : padding.vertical());
        }

        final var alongMainAxis = horizontal == (box.direction() == RenderNode.Direction.ROW);
        var total = 0;
        var flowCount = 0;

        for (final var child : box.children()) {
            if (child.layoutProps().absolute()) {
                continue;
            }

            final var size = measure(child, horizontal) + marginSize(child, horizontal);
            total = alongMainAxis ? total + size : Math.max(total, size);
            flowCount++;
        }

        if (alongMainAxis && flowCount > 0) {
            total += box.gap() * (flowCount - 1);
        }

        return total + (horizontal ? padding.horizontal() : padding.vertical());
    }

    private int measureBoxHeight(RenderNode.Box box, int width) {
        final var padding = box.layoutProps().padding();
        final var innerWidth = Math.max(0, width - padding.horizontal());
        final var row = box.direction() == RenderNode.Direction.ROW;
        final var stack = box.direction() == RenderNode.Direction.STACK;
        final var widths = row ? flowMainSizes(box, true, innerWidth, 0) : new int[0];
        var height = 0;
        var flowCount = 0;

        for (int i = 0; i < box.children().size(); i++) {
            final var child = box.children().get(i);
            if (child.layoutProps().absolute()) {
                continue;
            }

            final var margin = child.layoutProps().margin();
            final var availableWidth = Math.max(0, innerWidth - margin.horizontal());
            final var childWidth = row ? widths[i] : crossSize(child, false, availableWidth);
            final var childHeight = measureHeightForWidth(child, childWidth) + margin.vertical();
            height = row || stack ? Math.max(height, childHeight) : height + childHeight;
            flowCount++;
        }

        if (!row && !stack && flowCount > 0) {
            height += box.gap() * (flowCount - 1);
        }

        return height + padding.vertical();
    }

    private static int gridSize(RenderNode.Grid grid, boolean horizontal) {
        return gridSize(grid, horizontal, grid.width().isFixed() ? grid.width().pixels() : 0);
    }

    private static int gridSize(RenderNode.Grid grid, boolean horizontal, int width) {
        final var padding = grid.layoutProps().padding();
        final var columns = grid.columns() > 0 ? grid.columns()
                : width > 0 ? autoColumns(grid, width) : 1;
        final var count = (int) grid.children().stream().filter(child -> !child.layoutProps().absolute()).count();
        final var rows = ((long) count + columns - 1) / columns;
        final var size = horizontal
                ? (long) columns * grid.cellWidth() + (long) Math.max(0, columns - 1) * grid.gap()
                        + padding.horizontal()
                : rows * grid.cellHeight() + Math.max(0, rows - 1) * (long) grid.gap() + padding.vertical();

        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    private int measureTabs(RenderNode.Tabs tabs, boolean horizontal) {
        if (!horizontal) {
            return 20 + measureHeight(tabs.content());
        }

        int headers = 0;

        for (final var header : tabs.headers()) {
            headers += measureWidth(header);
        }

        return Math.max(headers, measureWidth(tabs.content()));
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
            case RenderNode.Grid grid -> placeGrid(grid, x, y, width, height);
            case RenderNode.Scroll scroll -> placeScroll(scroll, x, y, width, height);
            case RenderNode.Tabs tabs -> placeTabs(tabs, x, y, width, height);
            default -> new LaidOutNode(node, x, y, width, height, List.of());
        };
    }

    private LaidOutNode placeTabs(RenderNode.Tabs tabs, int x, int y, int width, int height) {
        final var children = new ArrayList<LaidOutNode>();
        int headerX = x;

        for (final var header : tabs.headers()) {
            final var headerWidth = measureWidth(header);
            children.add(place(header, headerX, y, headerWidth, 20));
            headerX += headerWidth;
        }

        children.add(placeBox(tabs.content(), x, y + 20, width, Math.max(0, height - 20)));

        return new LaidOutNode(tabs, x, y, width, height, children);
    }

    private LaidOutNode placeScroll(RenderNode.Scroll scroll, int x, int y, int width, int height) {
        final var padding = scroll.layoutProps().padding();
        final var contentWidth = scrollContentWidth(scroll, width);
        final var content = placeBox(
                scroll.content(),
                x + padding.left() - scroll.offsetX(),
                y + padding.top() - scroll.offsetY(),
                contentWidth,
                measureHeightForWidth(scroll.content(), contentWidth));

        return new LaidOutNode(scroll, x, y, width, height, List.of(content));
    }

    private int scrollContentWidth(RenderNode.Scroll scroll, int width) {
        final var innerWidth = Math.max(0, width - scroll.layoutProps().padding().horizontal());

        return Math.max(innerWidth, measureWidth(scroll.content()));
    }

    private LaidOutNode placeGrid(RenderNode.Grid grid, int x, int y, int width, int height) {
        final var padding = grid.layoutProps().padding();
        final var innerX = x + padding.left();
        final var innerY = y + padding.top();
        final var innerWidth = Math.max(0, width - padding.horizontal());
        final var innerHeight = Math.max(0, height - padding.vertical());
        final var columns = grid.columns() > 0 ? grid.columns() : autoColumns(grid, width);
        final var children = new ArrayList<LaidOutNode>();
        var cellIndex = 0;

        for (final var child : grid.children()) {
            if (child.layoutProps().absolute()) {
                children.add(placeAbsolute(child, innerX, innerY, innerWidth, innerHeight));
                continue;
            }

            final var column = cellIndex % columns;
            final var row = cellIndex / columns;
            final var margin = child.layoutProps().margin();
            final var availableWidth = Math.max(0, grid.cellWidth() - margin.horizontal());
            final var availableHeight = Math.max(0, grid.cellHeight() - margin.vertical());
            final var childWidth = child.width().isFill()
                    ? clamp(child, availableWidth, true) : Math.min(availableWidth, measureWidth(child));
            final var childHeight = child.height().isFill()
                    ? clamp(child, availableHeight, false)
                    : Math.min(availableHeight, measureHeightForWidth(child, childWidth));
            final var childX = innerX + column * (grid.cellWidth() + grid.gap())
                    + crossOffset(grid.align(), grid.cellWidth() - childWidth - margin.horizontal()) + margin.left();
            final var childY = innerY + row * (grid.cellHeight() + grid.gap())
                    + startOffset(grid.justify(), grid.cellHeight() - childHeight - margin.vertical()) + margin.top();
            children.add(placeRelative(child, childX, childY, childWidth, childHeight));
            cellIndex++;
        }

        return new LaidOutNode(grid, x, y, width, height, children);
    }

    private static int autoColumns(RenderNode.Grid grid, int width) {
        final var innerWidth = Math.max(0, width - grid.layoutProps().padding().horizontal());
        final var cellSpan = (long) grid.cellWidth() + grid.gap();

        return (int) Math.max(1, ((long) innerWidth + grid.gap()) / Math.max(1, cellSpan));
    }

    private LaidOutNode placeBox(RenderNode.Box box, int x, int y, int width, int height) {
        if (box.direction() == RenderNode.Direction.STACK) {
            return placeStack(box, x, y, width, height);
        }

        final var padding = box.layoutProps().padding();
        final var innerX = x + padding.left();
        final var innerY = y + padding.top();
        final var innerWidth = Math.max(0, width - padding.horizontal());
        final var innerHeight = Math.max(0, height - padding.vertical());
        final var row = box.direction() == RenderNode.Direction.ROW;
        final var innerMain = row ? innerWidth : innerHeight;
        final var innerCross = row ? innerHeight : innerWidth;
        final var children = box.children();
        final var mainSizes = flowMainSizes(box, row, innerMain, innerCross);
        var flowCount = 0;
        long used = 0;

        for (int i = 0; i < children.size(); i++) {
            if (!children.get(i).layoutProps().absolute()) {
                used += mainSizes[i] + marginSize(children.get(i), row);
                flowCount++;
            }
        }

        used += (long) box.gap() * Math.max(0, flowCount - 1);
        final var leftover = (int) Math.max(0, innerMain - used);
        var offset = startOffset(box.justify(), leftover);
        final var spacing = box.gap() + betweenSpacing(box.justify(), leftover, flowCount);
        final var placed = new ArrayList<LaidOutNode>();

        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            if (child.layoutProps().absolute()) {
                placed.add(placeAbsolute(child, innerX, innerY, innerWidth, innerHeight));
                continue;
            }

            final var margin = child.layoutProps().margin();
            final var crossSpace = Math.max(0, innerCross - (row ? margin.vertical() : margin.horizontal()));
            final var cross = row && !child.height().isFill()
                    ? measureHeightForWidth(child, mainSizes[i]) : crossSize(child, row, crossSpace);
            final var crossFree = innerCross - cross - (row ? margin.vertical() : margin.horizontal());
            final var crossOffset = crossOffset(box.align(), crossFree);
            offset += row ? margin.left() : margin.top();

            final var childX = row ? innerX + offset : innerX + crossOffset + margin.left();
            final var childY = row ? innerY + crossOffset + margin.top() : innerY + offset;
            placed.add(placeRelative(child, childX, childY, row ? mainSizes[i] : cross,
                    row ? cross : mainSizes[i]));
            offset += mainSizes[i] + (row ? margin.right() : margin.bottom()) + spacing;
        }

        return new LaidOutNode(box, x, y, width, height, placed);
    }

    private int[] flowMainSizes(RenderNode.Box box, boolean row, int innerMain, int innerCross) {
        final var children = box.children();
        final var sizes = new int[children.size()];
        final var minima = new int[children.size()];
        final var maxima = new int[children.size()];
        final var grow = new double[children.size()];
        final var shrink = new double[children.size()];
        var flowCount = 0;
        long used = 0;

        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            if (child.layoutProps().absolute()) {
                continue;
            }

            final var declared = mainSize(child, row);
            final var crossSpace = Math.max(0, innerCross - (row
                    ? child.layoutProps().margin().vertical() : child.layoutProps().margin().horizontal()));
            final var childWidth = row ? 0 : crossSize(child, false, crossSpace);
            sizes[i] = declared.isFill() ? minimum(child, row)
                    : row ? measureWidth(child) : measureHeightForWidth(child, childWidth);
            minima[i] = minimum(child, row);
            maxima[i] = maximum(child, row);
            grow[i] = child.layoutProps().grow() + (declared.isFill() ? 1 : 0);
            shrink[i] = child.layoutProps().shrink();
            used += sizes[i] + marginSize(child, row);
            flowCount++;
        }

        used += (long) box.gap() * Math.max(0, flowCount - 1);
        if (used < innerMain) {
            FlowSizing.grow(sizes, maxima, grow, (int) (innerMain - used));
        }

        if (used > innerMain) {
            FlowSizing.shrink(sizes, minima, shrink, (int) Math.min(Integer.MAX_VALUE, used - innerMain));
        }

        return sizes;
    }

    private LaidOutNode placeStack(RenderNode.Box box, int x, int y, int width, int height) {
        final var padding = box.layoutProps().padding();
        final var innerX = x + padding.left();
        final var innerY = y + padding.top();
        final var innerWidth = Math.max(0, width - padding.horizontal());
        final var innerHeight = Math.max(0, height - padding.vertical());
        final var placed = new ArrayList<LaidOutNode>();

        for (final var child : box.children()) {
            if (child.layoutProps().absolute()) {
                placed.add(placeAbsolute(child, innerX, innerY, innerWidth, innerHeight));
                continue;
            }

            final var margin = child.layoutProps().margin();
            final var availableWidth = Math.max(0, innerWidth - margin.horizontal());
            final var availableHeight = Math.max(0, innerHeight - margin.vertical());
            final var childWidth = child.width().isFill()
                    ? clamp(child, availableWidth, true) : Math.min(availableWidth, measureWidth(child));
            final var childHeight = child.height().isFill()
                    ? clamp(child, availableHeight, false)
                    : Math.min(availableHeight, measureHeightForWidth(child, childWidth));
            final var childX = innerX + crossOffset(box.align(), innerWidth - childWidth - margin.horizontal())
                    + margin.left();
            final var childY = innerY + startOffset(box.justify(), innerHeight - childHeight - margin.vertical())
                    + margin.top();
            placed.add(placeRelative(child, childX, childY, childWidth, childHeight));
        }

        return new LaidOutNode(box, x, y, width, height, placed);
    }

    private LaidOutNode placeAbsolute(
            RenderNode child,
            int innerX,
            int innerY,
            int innerWidth,
            int innerHeight
    ) {
        final var layout = child.layoutProps();
        final var margin = layout.margin();
        final var availableWidth = Math.max(0, innerWidth - margin.horizontal());
        final var availableHeight = Math.max(0, innerHeight - margin.vertical());
        final var width = clamp(child, resolve(child.width(), measureWidth(child), availableWidth), true);
        final var height = clamp(child, resolve(child.height(), measureHeight(child), availableHeight), false);
        final var childX = layout.right() == null
                ? innerX + margin.left() + (layout.left() == null ? 0 : layout.left())
                : innerX + innerWidth - margin.right() - layout.right() - width;
        final var childY = layout.bottom() == null
                ? innerY + margin.top() + (layout.top() == null ? 0 : layout.top())
                : innerY + innerHeight - margin.bottom() - layout.bottom() - height;

        return place(child, childX, childY, width, height);
    }

    private LaidOutNode placeRelative(RenderNode child, int x, int y, int width, int height) {
        final var layout = child.layoutProps();
        final var offsetX = layout.left() != null ? layout.left()
                : layout.right() != null ? -layout.right() : 0;
        final var offsetY = layout.top() != null ? layout.top()
                : layout.bottom() != null ? -layout.bottom() : 0;

        return place(child, x + offsetX, y + offsetY, width, height);
    }

    private static Size mainSize(RenderNode child, boolean row) {
        return row ? child.width() : child.height();
    }

    private static int marginSize(RenderNode child, boolean horizontal) {
        final var margin = child.layoutProps().margin();

        return horizontal ? margin.horizontal() : margin.vertical();
    }

    private static int minimum(RenderNode child, boolean horizontal) {
        final var bounds = child instanceof RenderNode.Box box ? box.bounds() : child.layoutProps().bounds();

        return horizontal ? bounds.minWidth() : bounds.minHeight();
    }

    private static int maximum(RenderNode child, boolean horizontal) {
        final var bounds = child instanceof RenderNode.Box box ? box.bounds() : child.layoutProps().bounds();

        return horizontal ? bounds.maxWidth() : bounds.maxHeight();
    }

    private int crossSize(RenderNode child, boolean row, int innerCross) {
        final var declared = row ? child.height() : child.width();
        if (declared.isFill()) {
            return clamp(child, innerCross, !row);
        }

        return measure(child, !row);
    }

    private static int clamp(RenderNode node, int value, boolean horizontal) {
        final var bounds = node instanceof RenderNode.Box box ? box.bounds() : node.layoutProps().bounds();

        return bounds.clamp(value, horizontal);
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
