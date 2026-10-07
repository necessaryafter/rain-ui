package com.rainframework.ui.client.expand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rainframework.ui.client.render.Alignment;
import com.rainframework.ui.client.render.Justify;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.client.render.Size;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.TypeSchema;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Turns a validated contract and validated properties into the tree the client draws. {@code show}, {@code match}
 * and {@code list} are transparent: what they produce is inserted into their parent, so a list inside a row lays its
 * items out in that row.
 */
@RequiredArgsConstructor
public final class Expander {
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_ITEM_SIZE = 16;

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{3}(?:[0-9A-Fa-f]{3})?$");

    private final Contract contract;
    private int updateEpoch;
    private Set<String> presentInputs = Set.of();
    private Set<String> presentTabs = Set.of();
    private Set<String> presentScrolls = Set.of();

    /** The root is always a box, so the layout has one node to place even when the root expands to several. */
    public RenderNode.Box expand(JsonNode properties) {
        return expand(properties, new HashMap<>());
    }

    /** Values belong to the screen instance, not the contract: an update only initializes a newly appearing input. */
    public RenderNode.Box expand(JsonNode properties, Map<String, String> inputValues) {
        return expand(properties, inputValues, new HashMap<>());
    }

    public RenderNode.Box expand(JsonNode properties, Map<String, String> inputValues, Map<String, String> tabs) {
        return expand(properties, inputValues, tabs, new HashMap<>());
    }

    public RenderNode.Box expand(
            JsonNode properties,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets
    ) {
        return expand(properties, inputValues, tabs, scrollOffsets, 0);
    }

    public RenderNode.Box expand(
            JsonNode properties,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            int epoch
    ) {
        updateEpoch = epoch;
        presentInputs = new HashSet<>();
        presentTabs = new HashSet<>();
        presentScrolls = new HashSet<>();
        final var scope = new Scope(properties, contract.properties());
        final var nodes = expandNode(contract.root(), scope, inputValues, tabs, scrollOffsets,
                new Identity("root", "screen"));

        inputValues.keySet().retainAll(presentInputs);
        tabs.keySet().retainAll(presentTabs);
        scrollOffsets.keySet().retainAll(presentScrolls);

        if (nodes.size() == 1 && nodes.getFirst() instanceof RenderNode.Box box) {
            return box;
        }

        return column(nodes);
    }

    private List<RenderNode> expandNode(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        return switch (node.type()) {
            case "column" -> List.of(box(node, RenderNode.Direction.COLUMN, scope,
                    inputValues, tabs, scrollOffsets, identity));
            case "row" -> List.of(box(node, RenderNode.Direction.ROW, scope,
                    inputValues, tabs, scrollOffsets, identity));
            case "box" -> List.of(box(node, RenderNode.Direction.COLUMN, scope,
                    inputValues, tabs, scrollOffsets, identity));
            case "stack" -> List.of(box(node, RenderNode.Direction.STACK, scope,
                    inputValues, tabs, scrollOffsets, identity));
            case "scroll" -> List.of(scroll(node, scope, inputValues, tabs, scrollOffsets, identity));
            case "grid" -> List.of(grid(node, scope, inputValues, tabs, scrollOffsets, identity));
            case "text" -> List.of(text(node, scope));
            case "item" -> item(node, scope);
            case "image" -> image(node, scope);
            case "button" -> List.of(button(node, scope, inputValues, tabs, scrollOffsets, identity));
            case "input" -> List.of(input(node, scope, inputValues, identity));
            case "list" -> list(node, scope, inputValues, tabs, scrollOffsets, identity);
            case "show" -> show(node, scope, inputValues, tabs, scrollOffsets, identity);
            case "match" -> match(node, scope, inputValues, tabs, scrollOffsets, identity);
            case "tabs" -> List.of(tabs(node, scope, inputValues, tabs, scrollOffsets, identity));
            default -> List.of();
        };
    }

    private List<RenderNode> expandChildren(
            List<ComponentNode> children,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var expanded = new ArrayList<RenderNode>();

        for (int i = 0; i < children.size(); i++) {
            expanded.addAll(expandNode(children.get(i), scope, inputValues, tabs, scrollOffsets, identity.child(i)));
        }

        return expanded;
    }

    private RenderNode.Box box(
            ComponentNode node,
            RenderNode.Direction direction,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var props = node.props();
        final var style = visualStyle(props, scope);

        return new RenderNode.Box(
                direction,
                intProp(scope.value(props.get("gap")), 0),
                intProp(scope.value(props.get("padding")), 0),
                alignment(scope.value(props.get("align"))),
                justify(scope.value(props.get("justify"))),
                size(scope.value(props.get("width"))),
                size(scope.value(props.get("height"))),
                expandChildren(node.children(), scope, inputValues, tabs, scrollOffsets, identity),
                style,
                bounds(props, scope),
                layoutProps(props, scope),
                node.type().equals("row") || node.type().equals("column"));
    }

    private RenderNode.Text text(ComponentNode node, Scope scope) {
        final var props = node.props();
        final var value = scope.value(props.get("value"));
        final var font = props.get("font");

        final var resolvedColor = color(scope.value(props.get("color")));

        return new RenderNode.Text(
                value == null ? "" : value.asText(),
                resolvedColor == 0 ? DEFAULT_TEXT_COLOR : resolvedColor,
                props.containsKey("shadow") && props.get("shadow").asBoolean(),
                font == null ? null : font.path("$asset").asText(null));
    }

    // An absent optional item draws nothing.
    private List<RenderNode> item(ComponentNode node, Scope scope) {
        final var value = scope.value(node.props().get("value"));
        if (value == null) {
            return List.of();
        }

        return List.of(new RenderNode.Item(
                value.asText(),
                intProp(scope.value(node.props().get("size")), DEFAULT_ITEM_SIZE)));
    }

    // A static source names the hash directly; a bound source carries a name the server picked from assetNames.
    private List<RenderNode> image(ComponentNode node, Scope scope) {
        final var props = node.props();
        final var src = props.get("src");
        final var hash = src.has("$asset") ? src.get("$asset").asText() : assetHash(scope.value(src));
        final var info = hash == null ? null : contract.assets().get(hash);
        if (info == null) {
            return List.of();
        }

        final var style = visualStyle(props, scope);

        return List.of(new RenderNode.Image(
                hash,
                info.width().intValue(),
                info.height().intValue(),
                size(scope.value(props.get("width"))),
                size(scope.value(props.get("height"))),
                style.zIndex(),
                layoutProps(props, scope),
                style));
    }

    private @Nullable String assetHash(@Nullable JsonNode name) {
        return name == null ? null : contract.assetNames().get(name.asText());
    }

    private RenderNode.Button button(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var props = node.props();
        final var disabled = scope.value(props.get("disabled"));
        final var payloadAtClick = payloadSupplier(props, scope, inputValues, identity.scopePath());
        final var style = visualStyle(props, scope);

        return new RenderNode.Button(
                props.get("action").asText(),
                payloadAtClick.get(),
                disabled != null && disabled.asBoolean(),
                column(expandChildren(node.children(), scope, inputValues, tabs, scrollOffsets, identity)),
                style.zIndex(),
                payloadAtClick,
                size(scope.value(props.get("width"))),
                size(scope.value(props.get("height"))),
                layoutProps(props, scope),
                style);
    }

    private Supplier<String> payloadSupplier(
            Map<String, JsonNode> props,
            Scope scope,
            Map<String, String> inputValues,
            String scopePath
    ) {
        return () -> {
            if (!props.containsKey("payload")) {
                return "{}";
            }

            final var resolved = resolvePayload(props.get("payload"), scope, inputValues, scopePath);

            return resolved == null ? "{}" : resolved.toString();
        };
    }

    private RenderNode.Input input(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Identity identity
    ) {
        final var props = node.props();
        final var multiline = props.containsKey("multiline") && props.get("multiline").asBoolean();
        final var value = scope.value(props.get("value"));
        final var placeholder = scope.value(props.get("placeholder"));
        final var id = props.get("id").asText();
        final var stateId = identity.input(id);
        final var initial = value == null ? "" : value.asText();
        final var current = inputValues.putIfAbsent(stateId, initial);
        presentInputs.add(stateId);

        return new RenderNode.Input(
                id,
                stateId,
                current == null ? initial : current,
                placeholder == null ? "" : placeholder.asText(),
                multiline,
                intProp(scope.value(props.get("maxLength")), 1024),
                props.containsKey("width") ? size(scope.value(props.get("width"))) : Size.FIT,
                props.containsKey("height") ? size(scope.value(props.get("height"))) : Size.fixed(multiline ? 72 : 20),
                layoutProps(props, scope));
    }

    private RenderNode.Grid grid(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var props = node.props();
        final var style = visualStyle(props, scope);

        return new RenderNode.Grid(
                intProp(scope.value(props.get("cellWidth")), 1),
                intProp(scope.value(props.get("cellHeight")), 1),
                intProp(scope.value(props.get("gap")), 0),
                intProp(scope.value(props.get("padding")), 0),
                intProp(scope.value(props.get("columns")), 0),
                size(scope.value(props.get("width"))),
                size(scope.value(props.get("height"))),
                expandChildren(node.children(), scope, inputValues, tabs, scrollOffsets, identity),
                style.zIndex(),
                layoutProps(props, scope),
                alignment(scope.value(props.get("align"))),
                justify(scope.value(props.get("justify"))),
                style);
    }

    private RenderNode.Scroll scroll(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var props = node.props();
        final var id = identity.nodePath();
        final var offset = scrollOffsets.getOrDefault(id, new int[2]);
        final var direction = scope.value(props.get("direction"));
        final var style = visualStyle(props, scope);
        presentScrolls.add(id);

        return new RenderNode.Scroll(
                id,
                direction == null ? "vertical" : direction.asText(),
                offset[0],
                offset[1],
                size(scope.value(props.get("width"))),
                size(scope.value(props.get("height"))),
                column(expandChildren(node.children(), scope, inputValues, tabs, scrollOffsets, identity)),
                style.zIndex(),
                layoutProps(props, scope),
                style);
    }

    // Bindings become their values; an absent optional value is left out of the payload instead of sent as null.
    private @Nullable JsonNode resolvePayload(
            JsonNode value,
            Scope scope,
            Map<String, String> inputValues,
            String scopePath
    ) {
        if (value.isObject() && value.size() == 1 && value.path("$input").isTextual()) {
            final var id = value.path("$input").asText();

            return JsonNodeFactory.instance.textNode(
                    inputValues.getOrDefault(scopePath + "/input:" + id,
                            inputValues.getOrDefault("screen/input:" + id, "")));
        }

        if (Scope.isBinding(value)) {
            return scope.value(value);
        }

        if (value.isObject()) {
            final ObjectNode resolved = JsonNodeFactory.instance.objectNode();

            for (final var entry : value.properties()) {
                final var field = resolvePayload(entry.getValue(), scope, inputValues, scopePath);
                if (field != null) {
                    resolved.set(entry.getKey(), field);
                }
            }

            return resolved;
        }

        if (value.isArray()) {
            final ArrayNode resolved = JsonNodeFactory.instance.arrayNode();

            for (final var item : value) {
                final var element = resolvePayload(item, scope, inputValues, scopePath);
                resolved.add(element == null ? JsonNodeFactory.instance.nullNode() : element);
            }

            return resolved;
        }

        return value;
    }

    // Only the innermost item is in scope inside a list's template (decisions.md §2).
    private List<RenderNode> list(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var source = scope.value(node.props().get("source"));
        final var itemFields = scope.itemFields(node.props().get("source"));
        if (source == null || !source.isArray() || itemFields == null || node.children().isEmpty()) {
            return List.of();
        }

        final var template = node.children().getFirst();
        final var expanded = new ArrayList<RenderNode>();
        final var resolvedKeys = new HashSet<String>();
        int index = 0;

        for (final var item : source) {
            final var itemScope = new Scope(item, itemFields);
            final var itemIdentity = identity.item(template.key(), itemScope, index, updateEpoch);
            if (template.key() != null && !resolvedKeys.add(itemIdentity.nodePath())) {
                throw new IllegalArgumentException("DUPLICATE_KEY at " + identity.nodePath());
            }

            expanded.addAll(expandNode(template, itemScope, inputValues, tabs, scrollOffsets, itemIdentity));
            index++;
        }

        return expanded;
    }

    private List<RenderNode> show(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        if (hasValue(scope.value(node.props().get("when")))) {
            final var expanded = new ArrayList<RenderNode>();

            for (int i = 0; i < node.children().size(); i++) {
                final var child = node.children().get(i);
                if (child.type().equals("fallback")) {
                    continue;
                }

                expanded.addAll(expandNode(child, scope, inputValues, tabs, scrollOffsets, identity.child(i)));
            }

            return expanded;
        }

        for (int i = 0; i < node.children().size(); i++) {
            final var child = node.children().get(i);
            if (child.type().equals("fallback")) {
                return expandChildren(child.children(), scope, inputValues, tabs, scrollOffsets, identity.child(i));
            }
        }

        return List.of();
    }

    // A bool shows on true; any other value shows when present and not empty ("" and [] count as empty).
    private static boolean hasValue(@Nullable JsonNode value) {
        if (value == null) {
            return false;
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isTextual()) {
            return !value.asText().isEmpty();
        }

        if (value.isArray()) {
            return !value.isEmpty();
        }

        return true;
    }

    private List<RenderNode> match(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var value = scope.value(node.props().get("value"));

        for (int i = 0; i < node.children().size(); i++) {
            final var child = node.children().get(i);
            if (child.type().equals("default")) {
                return expandChildren(child.children(), scope, inputValues, tabs, scrollOffsets, identity.child(i));
            }

            if (value != null && sameLiteral(value, child.props().get("is"))) {
                return expandChildren(child.children(), scope, inputValues, tabs, scrollOffsets, identity.child(i));
            }
        }

        return List.of();
    }

    private RenderNode.Tabs tabs(
            ComponentNode node,
            Scope scope,
            Map<String, String> inputValues,
            Map<String, String> tabs,
            Map<String, int[]> scrollOffsets,
            Identity identity
    ) {
        final var defaultTab = node.props().get("defaultTab").asText();
        final var tabsId = identity.nodePath();
        final var selected = tabs.getOrDefault(tabsId, defaultTab);
        final var headers = new ArrayList<RenderNode.TabHeader>();
        ComponentNode selectedPanel = null;
        int index = 0;
        presentTabs.add(tabsId);

        for (final var child : node.children()) {
            if (!child.type().equals("tab")) {
                index++;
                continue;
            }

            final var id = child.props().get("id").asText();
            final var label = scope.value(child.props().get("label"));
            headers.add(new RenderNode.TabHeader(tabsId, id, label == null ? id : label.asText(), selected.equals(id)));
            if (selected.equals(id)) {
                selectedPanel = child;
            } else {
                expandChildren(child.children(), scope, inputValues, tabs, scrollOffsets, identity.child(index));
            }

            index++;
        }

        final var content = selectedPanel == null
                ? column(List.of())
                : column(expandChildren(selectedPanel.children(), scope, inputValues, tabs,
                        scrollOffsets, identity.child(node.children().indexOf(selectedPanel))));

        return new RenderNode.Tabs(tabsId, Size.FIT, Size.FIT, headers, content);
    }

    // Numbers compare by value, since the JSON parser may give an int and a long different node types.
    private static boolean sameLiteral(JsonNode value, @Nullable JsonNode literal) {
        if (literal == null) {
            return false;
        }

        if (value.isIntegralNumber() && literal.isIntegralNumber()) {
            return value.asLong() == literal.asLong();
        }

        return value.equals(literal);
    }

    private record Identity(String nodePath, String scopePath) {
        private Identity child(int index) {
            return new Identity(nodePath + "/child:" + index, scopePath);
        }

        private Identity item(JsonNode key, Scope scope, int index, int epoch) {
            if (key == null) {
                final var path = nodePath + "/item:" + index + "/epoch:" + epoch;

                return new Identity(path, path);
            }

            final var resolved = scope.value(key);
            if (resolved == null || resolved.isNull()) {
                throw new IllegalArgumentException("INVALID_KEY at " + nodePath);
            }

            final var keyValue = resolved.getNodeType() + ":" + resolved.asText();
            final var path = nodePath + "/key:" + keyValue.length() + ":" + keyValue;

            return new Identity(path, path);
        }

        private String input(String id) {
            return scopePath + "/input:" + id;
        }
    }

    private static RenderNode.Box column(List<RenderNode> children) {
        return new RenderNode.Box(
                RenderNode.Direction.COLUMN,
                0,
                0,
                Alignment.START,
                Justify.START,
                Size.FIT,
                Size.FIT,
                children);
    }

    private static int intProp(@Nullable JsonNode value, int fallback) {
        return value != null && value.isNumber() ? Math.max(0, value.asInt()) : fallback;
    }

    private static int signedIntProp(@Nullable JsonNode value, int fallback) {
        return value != null && value.isNumber() ? value.asInt() : fallback;
    }

    private static RenderNode.SizeBounds bounds(Map<String, JsonNode> props, Scope scope) {
        final var minWidth = intProp(scope.value(props.get("minWidth")), 0);
        final var minHeight = intProp(scope.value(props.get("minHeight")), 0);
        final var maxWidth = intProp(scope.value(props.get("maxWidth")), Integer.MAX_VALUE);
        final var maxHeight = intProp(scope.value(props.get("maxHeight")), Integer.MAX_VALUE);

        return new RenderNode.SizeBounds(
                minWidth, minHeight, Math.max(minWidth, maxWidth), Math.max(minHeight, maxHeight));
    }

    private static RenderNode.LayoutProps layoutProps(Map<String, JsonNode> props, Scope scope) {
        return new RenderNode.LayoutProps(
                insets(scope.value(props.get("padding"))),
                insets(scope.value(props.get("margin"))),
                "absolute".equals(valueText(scope.value(props.get("position")))),
                optionalInt(scope.value(props.get("left"))),
                optionalInt(scope.value(props.get("right"))),
                optionalInt(scope.value(props.get("top"))),
                optionalInt(scope.value(props.get("bottom"))),
                numeric(scope.value(props.get("grow"))),
                numeric(scope.value(props.get("shrink"))),
                bounds(props, scope));
    }

    private static RenderNode.VisualStyle visualStyle(Map<String, JsonNode> props, Scope scope) {
        return new RenderNode.VisualStyle(
                color(scope.value(props.get("background"))),
                opacity(scope.value(props.get("opacity"))),
                intProp(scope.value(props.get("borderWidth")), 0),
                color(scope.value(props.get("borderColor"))),
                "hidden".equals(valueText(scope.value(props.get("overflow")))),
                signedIntProp(scope.value(props.get("zIndex")), 0),
                visualTransform(props, scope));
    }

    private static RenderNode.VisualTransform visualTransform(Map<String, JsonNode> props, Scope scope) {
        final var scale = numberProp(scope.value(props.get("scale")), 1);

        return new RenderNode.VisualTransform(
                numberProp(scope.value(props.get("rotate")), 0),
                scale * numberProp(scope.value(props.get("scaleX")), 1),
                scale * numberProp(scope.value(props.get("scaleY")), 1),
                numberProp(scope.value(props.get("skewX")), 0),
                numberProp(scope.value(props.get("skewY")), 0));
    }

    private static RenderNode.Insets insets(@Nullable JsonNode value) {
        if (value == null) {
            return RenderNode.Insets.ZERO;
        }

        if (value.isNumber()) {
            return RenderNode.Insets.uniform(intProp(value, 0));
        }

        return new RenderNode.Insets(
                intProp(value.get("top"), 0),
                intProp(value.get("right"), 0),
                intProp(value.get("bottom"), 0),
                intProp(value.get("left"), 0));
    }

    private static @Nullable Integer optionalInt(@Nullable JsonNode value) {
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private static double numeric(@Nullable JsonNode value) {
        return value != null && value.isNumber() ? value.asDouble() : 0;
    }

    private static double numberProp(@Nullable JsonNode value, double fallback) {
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private static Size size(@Nullable JsonNode value) {
        if (value == null) {
            return Size.FIT;
        }

        if (value.isNumber()) {
            return Size.fixed(value.asInt());
        }

        return value.asText().equals("fill") ? Size.FILL : Size.FIT;
    }

    private static Alignment alignment(@Nullable JsonNode value) {
        final var name = value == null ? "" : value.asText();
        return switch (name) {
            case "center" -> Alignment.CENTER;
            case "end" -> Alignment.END;
            default -> Alignment.START;
        };
    }

    private static Justify justify(@Nullable JsonNode value) {
        final var name = value == null ? "" : value.asText();
        return switch (name) {
            case "center" -> Justify.CENTER;
            case "end" -> Justify.END;
            case "space-between" -> Justify.SPACE_BETWEEN;
            default -> Justify.START;
        };
    }

    // A bad runtime color falls back instead of reaching the painter.
    private static int color(@Nullable JsonNode value) {
        if (value == null || !value.isTextual() || !COLOR_PATTERN.matcher(value.asText()).matches()) {
            return 0;
        }

        final var hex = value.asText().substring(1);
        final var expanded = hex.length() == 3
                ? "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2)
                : hex;

        return 0xFF000000 | Integer.parseInt(expanded, 16);
    }

    private static float opacity(@Nullable JsonNode value) {
        return value != null && value.isNumber() ? Math.max(0.0F, Math.min(1.0F, (float) value.asDouble())) : 1.0F;
    }

    private static @Nullable String valueText(@Nullable JsonNode value) {
        return value == null || !value.isTextual() ? null : value.asText();
    }

    /**
     * The values a binding can reach, with their declared types so absent values can take their default. Bindings are
     * dotted paths through objects.
     */
    private record Scope(JsonNode values, Map<String, TypeSchema> fields) {

        static boolean isBinding(@Nullable JsonNode node) {
            return node != null && node.isObject() && node.size() == 1 && node.has("$bind");
        }

        // A literal is its own value; a binding resolves through the scope, and an absent value takes its default.
        @Nullable JsonNode value(@Nullable JsonNode prop) {
            if (prop == null) {
                return null;
            }

            if (!isBinding(prop)) {
                return prop;
            }

            JsonNode current = values;
            TypeSchema schema = null;
            Map<String, TypeSchema> currentFields = fields;

            for (final var segment : prop.get("$bind").asText().split("\\.")) {
                schema = currentFields == null ? null : currentFields.get(segment);
                current = current == null || current.isNull() ? null : current.get(segment);
                currentFields = schema != null && TypeSchema.unwrap(schema) instanceof TypeSchema.ObjectType object
                        ? object.fields()
                        : null;
            }

            if (current != null && !current.isNull()) {
                return current;
            }

            return schema instanceof TypeSchema.OptionalType optional ? optional.defaultValue() : null;
        }

        @Nullable Map<String, TypeSchema> itemFields(@Nullable JsonNode source) {
            if (!isBinding(source)) {
                return null;
            }

            Map<String, TypeSchema> currentFields = fields;
            TypeSchema schema = null;
            for (final var segment : source.get("$bind").asText().split("\\.")) {
                if (currentFields == null) {
                    return null;
                }

                schema = TypeSchema.unwrap(currentFields.get(segment));
                currentFields = schema instanceof TypeSchema.ObjectType object ? object.fields() : null;
            }

            if (!(schema instanceof TypeSchema.ListType list)) {
                return null;
            }

            return TypeSchema.unwrap(list.of()) instanceof TypeSchema.ObjectType item ? item.fields() : null;
        }
    }
}
