package com.rainframework.ui.client.render;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The screen as the client draws it: every binding resolved, every default applied, and {@code show}, {@code match}
 * and {@code list} already replaced by the children they produce.
 */
public sealed interface RenderNode {

    Size width();

    Size height();

    default LayoutProps layoutProps() {
        return LayoutProps.DEFAULT;
    }

    default VisualStyle style() {
        return switch (this) {
            case Box box -> box.style();
            case Image image -> image.style();
            case Grid grid -> grid.style();
            case Scroll scroll -> scroll.style();
            case Button button -> button.style();
            default -> VisualStyle.DEFAULT;
        };
    }

    default int zIndex() {
        return switch (this) {
            case Box box -> box.style().zIndex();
            case Image image -> image.zIndex();
            case Grid grid -> grid.zIndex();
            case Scroll scroll -> scroll.zIndex();
            case Button button -> button.zIndex();
            default -> 0;
        };
    }

    record Box(
            Direction direction,
            int gap,
            int padding,
            Alignment align,
            Justify justify,
            Size width,
            Size height,
            List<RenderNode> children,
            VisualStyle style,
            SizeBounds bounds,
            LayoutProps layoutProps,
            boolean legacyPanel
    ) implements RenderNode {
        public Box(
                Direction direction,
                int gap,
                int padding,
                Alignment align,
                Justify justify,
                Size width,
                Size height,
                List<RenderNode> children
        ) {
            this(direction, gap, padding, align, justify, width, height, children,
                    VisualStyle.DEFAULT, SizeBounds.DEFAULT, LayoutProps.legacy(padding), true);
        }

        public Box(
                Direction direction,
                int gap,
                int padding,
                Alignment align,
                Justify justify,
                Size width,
                Size height,
                List<RenderNode> children,
                VisualStyle style
        ) {
            this(direction, gap, padding, align, justify, width, height, children,
                    style, SizeBounds.DEFAULT, LayoutProps.legacy(padding), true);
        }

        public Box(
                Direction direction,
                int gap,
                int padding,
                Alignment align,
                Justify justify,
                Size width,
                Size height,
                List<RenderNode> children,
                VisualStyle style,
                SizeBounds bounds
        ) {
            this(direction, gap, padding, align, justify, width, height, children,
                    style, bounds, LayoutProps.legacy(padding), true);
        }

        public Box(
                Direction direction,
                int gap,
                int padding,
                Alignment align,
                Justify justify,
                Size width,
                Size height,
                List<RenderNode> children,
                VisualStyle style,
                SizeBounds bounds,
                LayoutProps layoutProps
        ) {
            this(direction, gap, padding, align, justify, width, height, children,
                    style, bounds, layoutProps, true);
        }
    }

    record Insets(int top, int right, int bottom, int left) {
        public static final Insets ZERO = new Insets(0, 0, 0, 0);

        public static Insets uniform(int value) {
            return new Insets(value, value, value, value);
        }

        public int horizontal() {
            return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
        }

        public int vertical() {
            return (int) Math.min(Integer.MAX_VALUE, (long) top + bottom);
        }
    }

    record LayoutProps(
            Insets padding,
            Insets margin,
            boolean absolute,
            Integer left,
            Integer right,
            Integer top,
            Integer bottom,
            double grow,
            double shrink,
            SizeBounds bounds
    ) {
        public static final LayoutProps DEFAULT = new LayoutProps(
                Insets.ZERO, Insets.ZERO, false, null, null, null, null, 0, 0, SizeBounds.DEFAULT);

        public static LayoutProps legacy(int padding) {
            return new LayoutProps(Insets.uniform(padding), Insets.ZERO, false, null, null, null, null,
                    0, 0, SizeBounds.DEFAULT);
        }
    }

    record SizeBounds(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        public static final SizeBounds DEFAULT = new SizeBounds(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

        public int clamp(int value, boolean horizontal) {
            return Math.max(horizontal ? minWidth : minHeight, Math.min(horizontal ? maxWidth : maxHeight, value));
        }
    }

    record VisualStyle(
            int backgroundColor,
            float opacity,
            int borderWidth,
            int borderColor,
            boolean clipChildren,
            int zIndex,
            VisualTransform transform
    ) {
        public static final VisualStyle DEFAULT = new VisualStyle(
                0, 1.0F, 0, 0, false, 0, VisualTransform.DEFAULT);

        public VisualStyle(int backgroundColor, float opacity, int borderWidth, int borderColor) {
            this(backgroundColor, opacity, borderWidth, borderColor, false, 0, VisualTransform.DEFAULT);
        }

        public VisualStyle(int backgroundColor, float opacity, int borderWidth, int borderColor, boolean clipChildren) {
            this(backgroundColor, opacity, borderWidth, borderColor, clipChildren, 0, VisualTransform.DEFAULT);
        }

        public VisualStyle(
                int backgroundColor,
                float opacity,
                int borderWidth,
                int borderColor,
                boolean clipChildren,
                int zIndex
        ) {
            this(backgroundColor, opacity, borderWidth, borderColor, clipChildren, zIndex, VisualTransform.DEFAULT);
        }

        public boolean hasBackground() {
            return (backgroundColor >>> 24) != 0;
        }

        public boolean hasBorder() {
            return borderWidth > 0 && (borderColor >>> 24) != 0;
        }
    }

    record VisualTransform(double rotate, double scaleX, double scaleY, double skewX, double skewY) {
        public static final VisualTransform DEFAULT = new VisualTransform(0, 1, 1, 0, 0);

        public boolean isIdentity() {
            return rotate == 0 && scaleX == 1 && scaleY == 1 && skewX == 0 && skewY == 0;
        }
    }

    record Text(String value, int color, boolean shadow, @Nullable String fontHash) implements RenderNode {

        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.FIT;
        }
    }

    /** {@code itemData} is the base64 of the vanilla network ItemStack codec, decoded by the version module. */
    record Item(String itemData, int size) implements RenderNode {

        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.FIT;
        }
    }

    record Image(
            String hash,
            int intrinsicWidth,
            int intrinsicHeight,
            Size width,
            Size height,
            int zIndex,
            LayoutProps layoutProps,
            VisualStyle style
    ) implements RenderNode {
        public Image(String hash, int intrinsicWidth, int intrinsicHeight, Size width, Size height) {
            this(hash, intrinsicWidth, intrinsicHeight, width, height, 0, LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Image(String hash, int intrinsicWidth, int intrinsicHeight, Size width, Size height, int zIndex) {
            this(hash, intrinsicWidth, intrinsicHeight, width, height, zIndex,
                    LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Image(
                String hash,
                int intrinsicWidth,
                int intrinsicHeight,
                Size width,
                Size height,
                int zIndex,
                LayoutProps layoutProps
        ) {
            this(hash, intrinsicWidth, intrinsicHeight, width, height, zIndex, layoutProps, VisualStyle.DEFAULT);
        }
    }

    /** Text editing state is owned by ScreenController; this node only describes its initial presentation. */
    record Input(
            String id,
            String stateId,
            String value,
            String placeholder,
            boolean multiline,
            int maxLength,
            Size width,
            Size height,
            LayoutProps layoutProps
    ) implements RenderNode {
        public Input(
                String id,
                String value,
                String placeholder,
                boolean multiline,
                int maxLength,
                Size width,
                Size height
        ) {
            this(id, id, value, placeholder, multiline, maxLength, width, height, LayoutProps.DEFAULT);
        }

        public Input(
                String id,
                String stateId,
                String value,
                String placeholder,
                boolean multiline,
                int maxLength,
                Size width,
                Size height
        ) {
            this(id, stateId, value, placeholder, multiline, maxLength, width, height, LayoutProps.DEFAULT);
        }
    }

    record Grid(
            int cellWidth,
            int cellHeight,
            int gap,
            int padding,
            int columns,
            Size width,
            Size height,
            List<RenderNode> children,
            int zIndex,
            LayoutProps layoutProps,
            Alignment align,
            Justify justify,
            VisualStyle style
    ) implements RenderNode {
        public Grid(
                int cellWidth,
                int cellHeight,
                int gap,
                int padding,
                int columns,
                Size width,
                Size height,
                List<RenderNode> children
        ) {
            this(cellWidth, cellHeight, gap, padding, columns, width, height, children,
                    0, LayoutProps.legacy(padding), Alignment.START, Justify.START, VisualStyle.DEFAULT);
        }

        public Grid(
                int cellWidth,
                int cellHeight,
                int gap,
                int padding,
                int columns,
                Size width,
                Size height,
                List<RenderNode> children,
                int zIndex
        ) {
            this(cellWidth, cellHeight, gap, padding, columns, width, height, children,
                    zIndex, LayoutProps.legacy(padding), Alignment.START, Justify.START, VisualStyle.DEFAULT);
        }

        public Grid(
                int cellWidth,
                int cellHeight,
                int gap,
                int padding,
                int columns,
                Size width,
                Size height,
                List<RenderNode> children,
                int zIndex,
                LayoutProps layoutProps
        ) {
            this(cellWidth, cellHeight, gap, padding, columns, width, height, children,
                    zIndex, layoutProps, Alignment.START, Justify.START, VisualStyle.DEFAULT);
        }

        public Grid(
                int cellWidth,
                int cellHeight,
                int gap,
                int padding,
                int columns,
                Size width,
                Size height,
                List<RenderNode> children,
                int zIndex,
                LayoutProps layoutProps,
                Alignment align,
                Justify justify
        ) {
            this(cellWidth, cellHeight, gap, padding, columns, width, height, children,
                    zIndex, layoutProps, align, justify, VisualStyle.DEFAULT);
        }
    }

    record Scroll(
            String id,
            String direction,
            int offsetX,
            int offsetY,
            Size width,
            Size height,
            Box content,
            int zIndex,
            LayoutProps layoutProps,
            VisualStyle style
    ) implements RenderNode {
        public Scroll(String id, String direction, int offsetX, int offsetY, Size width, Size height, Box content) {
            this(id, direction, offsetX, offsetY, width, height, content,
                    0, LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Scroll(
                String id,
                String direction,
                int offsetX,
                int offsetY,
                Size width,
                Size height,
                Box content,
                int zIndex
        ) {
            this(id, direction, offsetX, offsetY, width, height, content,
                    zIndex, LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Scroll(
                String id,
                String direction,
                int offsetX,
                int offsetY,
                Size width,
                Size height,
                Box content,
                int zIndex,
                LayoutProps layoutProps
        ) {
            this(id, direction, offsetX, offsetY, width, height, content,
                    zIndex, layoutProps, VisualStyle.DEFAULT);
        }
    }

    record Tabs(String id, Size width, Size height, List<TabHeader> headers, Box content) implements RenderNode {
    }

    record TabHeader(String tabsId, String id, String label, boolean selected) implements RenderNode {
        @Override
        public Size width() {
            return Size.FIT;
        }

        @Override
        public Size height() {
            return Size.fixed(20);
        }
    }

    /** {@code payloadJson} already has every binding replaced by its value. */
    record Button(
            String actionId,
            String payloadJson,
            boolean disabled,
            Box content,
            int zIndex,
            Supplier<String> payloadAtClick,
            Size width,
            Size height,
            LayoutProps layoutProps,
            VisualStyle style
    ) implements RenderNode {
        public Button(String actionId, String payloadJson, boolean disabled, Box content) {
            this(actionId, payloadJson, disabled, content, 0);
        }

        public Button(String actionId, String payloadJson, boolean disabled, Box content, int zIndex) {
            this(actionId, payloadJson, disabled, content, zIndex, () -> payloadJson,
                    Size.FIT, Size.FIT, LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Button(
                String actionId,
                String payloadJson,
                boolean disabled,
                Box content,
                int zIndex,
                Supplier<String> payloadAtClick
        ) {
            this(actionId, payloadJson, disabled, content, zIndex, payloadAtClick,
                    Size.FIT, Size.FIT, LayoutProps.DEFAULT, VisualStyle.DEFAULT);
        }

        public Button(
                String actionId,
                String payloadJson,
                boolean disabled,
                Box content,
                int zIndex,
                Supplier<String> payloadAtClick,
                Size width,
                Size height,
                LayoutProps layoutProps
        ) {
            this(actionId, payloadJson, disabled, content, zIndex, payloadAtClick,
                    width, height, layoutProps, VisualStyle.DEFAULT);
        }

        public String resolvedPayload() {
            return payloadAtClick.get();
        }
    }

    enum Direction {
        ROW,
        COLUMN,
        STACK
    }
}
