package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualValueValidationTest {
    private final VisualValueValidator validator = new VisualValueValidator();

    @Test
    void rejectsResolvedWidthOutsideBounds() throws Exception {
        final var result = validate("\"width\":{\"kind\":\"int\"}",
                "\"width\":{\"$bind\":\"width\"}", "[]", "{\"width\":-1}");

        assertMismatch(result, "properties.width");
    }

    @Test
    void rejectsResolvedColorAndOpacity() throws Exception {
        final var color = validate("\"color\":{\"kind\":\"string\"}",
                "\"background\":{\"$bind\":\"color\"}", "[]", "{\"color\":\"red\"}");
        final var opacity = validate("\"alpha\":{\"kind\":\"double\"}",
                "\"opacity\":{\"$bind\":\"alpha\"}", "[]", "{\"alpha\":2}");

        assertMismatch(color, "properties.color");
        assertMismatch(opacity, "properties.alpha");
    }

    @Test
    void rejectsResolvedValuesInsideListItems() throws Exception {
        final var result = validate("""
                "items":{"kind":"list","of":{"kind":"object","fields":{"width":{"kind":"int"}}}}
                """, "\"source\":{\"$bind\":\"items\"}", """
                [{"type":"box","key":"item","props":{"width":{"$bind":"width"}},"children":[]}]
                """, "{\"items\":[{\"width\":4},{\"width\":-2}]}", "list");

        assertMismatch(result, "properties.items[1].width");
    }

    @Test
    void acceptsResolvedValuesWithinBounds() throws Exception {
        final var result = validate("\"width\":{\"kind\":\"int\"}",
                "\"width\":{\"$bind\":\"width\"}", "[]", "{\"width\":12}");

        assertTrue(result.isValid());
    }

    @Test
    void rejectsAGridDimensionThatResolvesToZero() throws Exception {
        final var result = validate("\"width\":{\"kind\":\"int\"}",
                "\"cellWidth\":{\"$bind\":\"width\"},\"cellHeight\":10,\"columns\":2",
                "[]", "{\"width\":0}", "grid");

        assertMismatch(result, "properties.width");
    }

    private static void assertMismatch(ValidationResult result, String path) {
        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, result.getError().getCode());
        assertEquals(path, result.getError().getPath());
    }

    private ValidationResult validate(String schema, String props, String children, String values) throws Exception {
        return validate(schema, props, children, values, "box");
    }

    private ValidationResult validate(
            String schema,
            String props,
            String children,
            String values,
            String type
    ) throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:resolved-visual",
                  "properties":{%s},
                  "actions":{},
                  "root":{"type":"%s","props":{%s},"children":%s}
                }
                """.formatted(schema, type, props, children);
        final var contract = new ContractParser().parse(json);

        return validator.validate(contract, new ObjectMapper().readTree(values));
    }
}
