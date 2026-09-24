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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a validated contract and validated properties into the tree the client draws. {@code show}, {@code match}
 * and {@code list} are transparent: what they produce is inserted into their parent, so a list inside a row lays its
 * items out in that row.
 */
public final class Expander {
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_ITEM_SIZE = 16;

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final Contract contract;

    public Expander(Contract contract) {
        this.contract = contract;
    }

    /** The root is always a box, so the layout has one node to place even when the root expands to several. */
    public RenderNode.Box expand(JsonNode properties) {
        final var scope = new Scope(properties, contract.properties());
        final var nodes = expandNode(contract.root(), scope);

        if (nodes.size() == 1 && nodes.getFirst() instanceof RenderNode.Box box) {
            return box;
        }

        return column(nodes);
    }

    private List<RenderNode> expandNode(ComponentNode node, Scope scope) {
        return switch (node.type()) {
            case "column" -> List.of(box(node, RenderNode.Direction.COLUMN, scope));
            case "row" -> List.of(box(node, RenderNode.Direction.ROW, scope));
            case "text" -> List.of(text(node, scope));
            case "item" -> item(node, scope);
            case "image" -> image(node, scope);
            case "button" -> List.of(button(node, scope));
            case "list" -> list(node, scope);
            case "show" -> show(node, scope);
            case "match" -> match(node, scope);
            default -> List.of();
        };
    }

    private List<RenderNode> expandChildren(List<ComponentNode> children, Scope scope) {
        final var expanded = new ArrayList<RenderNode>();
        for (final var child : children) {
            expanded.addAll(expandNode(child, scope));
        }

        return expanded;
    }

    private RenderNode.Box box(ComponentNode node, RenderNode.Direction direction, Scope scope) {
        final var props = node.props();

        return new RenderNode.Box(
                direction,
                intProp(props, "gap", 0),
                intProp(props, "padding", 0),
                alignment(props.get("align")),
                justify(props.get("justify")),
                size(props.get("width")),
                size(props.get("height")),
                expandChildren(node.children(), scope));
    }

    private RenderNode.Text text(ComponentNode node, Scope scope) {
        final var props = node.props();
        final var value = scope.value(props.get("value"));
        final var font = props.get("font");

        return new RenderNode.Text(
                value == null ? "" : value.asText(),
                color(scope.value(props.get("color"))),
                props.containsKey("shadow") && props.get("shadow").asBoolean(),
                font == null ? null : font.path("$asset").asText(null));
    }

    // An absent optional item draws nothing.
    private List<RenderNode> item(ComponentNode node, Scope scope) {
        final var value = scope.value(node.props().get("value"));
        if (value == null) {
            return List.of();
        }

        return List.of(new RenderNode.Item(value.asText(), intProp(node.props(), "size", DEFAULT_ITEM_SIZE)));
    }

    // A static source names the hash directly; a bound source carries a name the server picked from assetNames.
    private List<RenderNode> image(ComponentNode node, Scope scope) {
        final var src = node.props().get("src");
        final var hash = src.has("$asset") ? src.get("$asset").asText() : assetHash(scope.value(src));
        final var info = hash == null ? null : contract.assets().get(hash);
        if (info == null) {
            return List.of();
        }

        return List.of(new RenderNode.Image(
                hash,
                info.width().intValue(),
                info.height().intValue(),
                size(node.props().get("width")),
                size(node.props().get("height"))));
    }

    private @Nullable String assetHash(@Nullable JsonNode name) {
        return name == null ? null : contract.assetNames().get(name.asText());
    }

    private RenderNode.Button button(ComponentNode node, Scope scope) {
        final var props = node.props();
        final var disabled = scope.value(props.get("disabled"));
        final var payload = props.containsKey("payload") ? resolvePayload(props.get("payload"), scope) : null;

        return new RenderNode.Button(
                props.get("action").asText(),
                payload == null ? "{}" : payload.toString(),
                disabled != null && disabled.asBoolean(),
                column(expandChildren(node.children(), scope)));
    }

    // Bindings become their values; an absent optional value is left out of the payload instead of sent as null.
    private @Nullable JsonNode resolvePayload(JsonNode value, Scope scope) {
        if (Scope.isBinding(value)) {
            return scope.value(value);
        }

        if (value.isObject()) {
            final ObjectNode resolved = JsonNodeFactory.instance.objectNode();
            for (final var entry : value.properties()) {
                final var field = resolvePayload(entry.getValue(), scope);
                if (field != null) {
                    resolved.set(entry.getKey(), field);
                }
            }

            return resolved;
        }

        if (value.isArray()) {
            final ArrayNode resolved = JsonNodeFactory.instance.arrayNode();
            for (final var item : value) {
                final var element = resolvePayload(item, scope);
                resolved.add(element == null ? JsonNodeFactory.instance.nullNode() : element);
            }

            return resolved;
        }

        return value;
    }

    // Only the innermost item is in scope inside a list's template (decisions.md §2).
    private List<RenderNode> list(ComponentNode node, Scope scope) {
        final var source = scope.value(node.props().get("source"));
        final var itemFields = scope.itemFields(node.props().get("source"));
        if (source == null || !source.isArray() || itemFields == null || node.children().isEmpty()) {
            return List.of();
        }

        final var template = node.children().getFirst();
        final var expanded = new ArrayList<RenderNode>();
        for (final var item : source) {
            expanded.addAll(expandNode(template, new Scope(item, itemFields)));
        }

        return expanded;
    }

    private List<RenderNode> show(ComponentNode node, Scope scope) {
        final var content = new ArrayList<ComponentNode>();
        ComponentNode fallback = null;

        for (final var child : node.children()) {
            if (child.type().equals("fallback")) {
                fallback = child;
                continue;
            }

            content.add(child);
        }

        if (hasValue(scope.value(node.props().get("when")))) {
            return expandChildren(content, scope);
        }

        return fallback == null ? List.of() : expandChildren(fallback.children(), scope);
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

    private List<RenderNode> match(ComponentNode node, Scope scope) {
        final var value = scope.value(node.props().get("value"));

        for (final var child : node.children()) {
            if (child.type().equals("default")) {
                return expandChildren(child.children(), scope);
            }

            if (value != null && sameLiteral(value, child.props().get("is"))) {
                return expandChildren(child.children(), scope);
            }
        }

        return List.of();
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

    private static int intProp(Map<String, JsonNode> props, String key, int fallback) {
        final var value = props.get(key);
        return value != null && value.isNumber() ? Math.max(0, value.asInt()) : fallback;
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

    // A color that does not match #RRGGBB, typically a bad value sent at runtime, falls back instead of failing.
    private static int color(@Nullable JsonNode value) {
        if (value == null || !value.isTextual() || !COLOR_PATTERN.matcher(value.asText()).matches()) {
            return DEFAULT_TEXT_COLOR;
        }

        return 0xFF000000 | Integer.parseInt(value.asText().substring(1), 16);
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
