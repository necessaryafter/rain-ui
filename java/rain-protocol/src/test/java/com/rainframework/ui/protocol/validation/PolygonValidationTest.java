package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonValidationTest {
    private final ContractValidator validator = new ContractValidator();

    @Test
    void acceptsAValidNormalizedPolygon() throws Exception {
        assertTrue(validate("[[0,1500],[10000,0],[9700,8000],[300,10000]]").isValid());
    }

    @Test
    void rejectsAZeroLengthEdge() throws Exception {
        final var result = validate("[[0,0],[10000,0],[10000,0],[0,10000]]");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_POLYGON, result.getError().getCode());
    }

    @Test
    void rejectsANonzeroAreaSelfIntersection() throws Exception {
        final var result = validate("[[0,0],[10000,0],[10000,10000],[0,5000],[10000,5000]]");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_POLYGON, result.getError().getCode());
    }

    @Test
    void rejectsCoordinatesOutsideTheIntegerRange() throws Exception {
        final var result = validate("[[4294967296,0],[10000,0],[0,10000]]");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_POLYGON, result.getError().getCode());
    }

    private ValidationResult validate(String points) throws Exception {
        final var json = """
                {
                  "schemaVersion": 0,
                  "id": "test:polygon",
                  "properties": {},
                  "actions": {},
                  "root": { "type": "box", "props": { "shape": %s }, "children": [] }
                }
                """.formatted(points);
        final var contract = new ContractParser().parse(json);

        return validator.validate(contract);
    }
}
