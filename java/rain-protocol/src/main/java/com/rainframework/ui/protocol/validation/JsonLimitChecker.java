package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.rainframework.ui.protocol.Limits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Checks the size limits of a JSON document in a streaming pass, before it is parsed into a tree, so an oversized or
 * deeply nested document never reaches the tree parser. The checks do not look at the declared schema.
 * <p>
 * Malformed JSON passes here on purpose: the tree parse that follows rejects it with its own error.
 */
public final class JsonLimitChecker {
    private static final JsonFactory factory = new JsonFactory();
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    public ValidationResult checkContract(String json) {
        return check(json, Limits.MAX_CONTRACT_BYTES, Limits.MAX_JSON_DEPTH, NO_LIMIT);
    }

    public ValidationResult checkProperties(String json) {
        return check(json, Limits.MAX_PROPERTIES_BYTES, Limits.MAX_JSON_DEPTH, Limits.MAX_LIST_ELEMENTS);
    }

    public ValidationResult checkPayload(String json) {
        return check(json, Limits.MAX_PAYLOAD_BYTES, Limits.MAX_PAYLOAD_DEPTH, NO_LIMIT);
    }

    private ValidationResult check(String json, int maxBytes, int maxDepth, int maxListElements) {
        if (json.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "root");
        }

        try (final var parser = factory.createParser(json)) {
            return scan(parser, maxDepth, maxListElements);
        } catch (IOException e) {
            return ValidationResult.ok();
        }
    }

    private ValidationResult scan(JsonParser parser, int maxDepth, int maxListElements) throws IOException {
        // One entry per open container: the element count for arrays, -1 for objects.
        final Deque<Integer> containers = new ArrayDeque<>();

        for (var token = parser.nextToken(); token != null; token = parser.nextToken()) {
            if (token.isStructEnd()) {
                containers.pop();
                continue;
            }

            if (token == JsonToken.FIELD_NAME) {
                continue;
            }

            final var enclosing = containers.peek();
            if (enclosing != null && enclosing >= 0) {
                containers.pop();
                containers.push(enclosing + 1);

                if (enclosing + 1 > maxListElements) {
                    final var array = token.isStructStart()
                            ? parser.getParsingContext().getParent()
                            : parser.getParsingContext();
                    return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, pathOf(array));
                }
            }

            if (!token.isStructStart()) {
                continue;
            }

            containers.push(token == JsonToken.START_ARRAY ? 0 : -1);
            if (containers.size() > maxDepth) {
                return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "root");
            }
        }

        return ValidationResult.ok();
    }

    // A container's own name or index is recorded on its parent context, not on itself.
    private static String pathOf(JsonStreamContext context) {
        final var path = new StringBuilder();
        var parent = context.getParent();

        while (parent != null && !parent.inRoot()) {
            final var segment = parent.inArray()
                    ? "[" + parent.getCurrentIndex() + "]"
                    : "." + parent.getCurrentName();

            path.insert(0, segment);
            parent = parent.getParent();
        }

        if (path.isEmpty()) {
            return "root";
        }

        return path.charAt(0) == '.' ? path.substring(1) : path.toString();
    }
}
