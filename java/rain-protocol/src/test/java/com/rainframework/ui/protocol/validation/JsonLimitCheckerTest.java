package com.rainframework.ui.protocol.validation;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLimitCheckerTest {
    private static final int MAX_PROPERTIES_BYTES = 256 * 1024;
    private static final int MAX_LIST_ELEMENTS = 1000;
    private static final int MAX_JSON_DEPTH = 128;
    private static final int MAX_PAYLOAD_BYTES = 8 * 1024;
    private static final int MAX_PAYLOAD_DEPTH = 8;

    private final JsonLimitChecker checker = new JsonLimitChecker();

    @Test
    void acceptsPropertiesAtTheByteLimit() {
        assertValid(checker.checkProperties(objectWithBytes(MAX_PROPERTIES_BYTES)));
    }

    @Test
    void rejectsPropertiesOverTheByteLimit() {
        assertLimitExceeded(checker.checkProperties(objectWithBytes(MAX_PROPERTIES_BYTES + 1)), "root");
    }

    @Test
    void acceptsAListAtTheElementLimit() {
        assertValid(checker.checkProperties(objectWithList(MAX_LIST_ELEMENTS)));
    }

    @Test
    void rejectsAListOverTheElementLimit() {
        assertLimitExceeded(checker.checkProperties(objectWithList(MAX_LIST_ELEMENTS + 1)), "items");
    }

    @Test
    void acceptsPropertiesAtTheDepthLimit() {
        assertValid(checker.checkProperties(objectWithDepth(MAX_JSON_DEPTH)));
    }

    @Test
    void rejectsPropertiesOverTheDepthLimit() {
        assertLimitExceeded(checker.checkProperties(objectWithDepth(MAX_JSON_DEPTH + 1)), "root");
    }

    @Test
    void acceptsAPayloadAtTheByteLimit() {
        assertValid(checker.checkPayload(objectWithBytes(MAX_PAYLOAD_BYTES)));
    }

    @Test
    void rejectsAPayloadOverTheByteLimit() {
        assertLimitExceeded(checker.checkPayload(objectWithBytes(MAX_PAYLOAD_BYTES + 1)), "root");
    }

    @Test
    void acceptsAPayloadAtTheDepthLimit() {
        assertValid(checker.checkPayload(objectWithDepth(MAX_PAYLOAD_DEPTH)));
    }

    @Test
    void rejectsAPayloadOverTheDepthLimit() {
        assertLimitExceeded(checker.checkPayload(objectWithDepth(MAX_PAYLOAD_DEPTH + 1)), "root");
    }

    // Padded with whitespace so no string length limit can trigger before the byte limit.
    private static String objectWithBytes(int bytes) {
        final var head = "{\"title\":\"x\"";
        final var tail = "}";

        return head + " ".repeat(bytes - head.length() - tail.length()) + tail;
    }

    private static String objectWithList(int elements) {
        final var items = String.join(",", Collections.nCopies(elements, "0"));

        return "{\"items\":[" + items + "]}";
    }

    // The top-level object counts as depth 1.
    private static String objectWithDepth(int depth) {
        return "{\"a\":".repeat(depth - 1) + "{}" + "}".repeat(depth - 1);
    }

    private static void assertValid(ValidationResult result) {
        assertTrue(result.isValid(), () -> "Expected valid, got " + result.getError().getCode()
                + " at " + result.getError().getPath());
    }

    private static void assertLimitExceeded(ValidationResult result, String path) {
        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.LIMIT_EXCEEDED, result.getError().getCode());
        assertEquals(path, result.getError().getPath());
    }
}
