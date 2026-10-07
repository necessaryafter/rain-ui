package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPropValidationTest {
    private final ContractValidator validator = new ContractValidator();

    @Test
    void checksVisualBindingDeclarationsAndTypes() throws Exception {
        assertError("box", "\"width\":{\"$bind\":\"missing\"}", "",
                ValidationErrorCode.UNDECLARED_BINDING, "root.props.width");
        assertError("box", "\"width\":{\"$bind\":\"wrong\"}", "",
                ValidationErrorCode.BINDING_TYPE_MISMATCH, "root.props.width");
        assertError("box", "\"background\":{\"$bind\":\"wrong\"}", "",
                ValidationErrorCode.BINDING_TYPE_MISMATCH, "root.props.background");

        assertTrue(validate("box", "\"width\":{\"$bind\":\"width\"},"
                + "\"background\":{\"$bind\":\"color\"}", "").isValid());
    }

    @Test
    void checksVisualLiteralsAndMutuallyExclusiveAnchors() throws Exception {
        assertError("box", "\"width\":-1", "", ValidationErrorCode.INVALID_PROP_VALUE, "root.props.width");
        assertError("box", "\"opacity\":1.5", "", ValidationErrorCode.INVALID_PROP_VALUE, "root.props.opacity");
        assertError("box", "\"left\":1,\"right\":2", "",
                ValidationErrorCode.INVALID_PROP_COMBINATION, "root.props.right");
        assertError("box", "\"margin\":{\"top\":1,\"diagonal\":2}", "",
                ValidationErrorCode.INVALID_PROP_TYPE, "root.props.margin");
        assertError("text", "\"value\":\"a\",\"fontWeight\":\"heavy\"", "",
                ValidationErrorCode.INVALID_PROP_VALUE, "root.props.fontWeight");
        assertError("text", "\"value\":\"a\",\"strokeWidth\":-1", "",
                ValidationErrorCode.INVALID_PROP_VALUE, "root.props.strokeWidth");
    }

    @Test
    void rejectsTransformAboveClippedContent() throws Exception {
        final var scroll = """
                ,"children":[{"type":"scroll","props":{"width":50,"height":50},"children":[]}]
                """;

        assertError("box", "\"rotate\":10", scroll,
                ValidationErrorCode.UNSUPPORTED_COMBINATION, "root.props.rotate");
        assertError("box", "\"skewX\":{\"$bind\":\"width\"}", scroll,
                ValidationErrorCode.UNSUPPORTED_COMBINATION, "root.props.skewX");
    }

    @Test
    void checksGridDimensionBindingTypes() throws Exception {
        assertTrue(validate("grid", "\"cellWidth\":{\"$bind\":\"width\"},"
                + "\"cellHeight\":20,\"width\":{\"$bind\":\"width\"}", "").isValid());
        assertError("grid", "\"cellWidth\":{\"$bind\":\"wrong\"},\"cellHeight\":20,\"width\":100",
                "", ValidationErrorCode.BINDING_TYPE_MISMATCH, "root.props.cellWidth");
    }

    @Test
    void checksInputAndTabStructure() throws Exception {
        assertError("input", "", "", ValidationErrorCode.INVALID_PROP_TYPE, "root.props.id");
        assertError("tab", "\"id\":\"a\",\"label\":\"A\"", "",
                ValidationErrorCode.MISPLACED_COMPONENT, "root");
        assertError("tabs", "\"defaultTab\":\"missing\"", """
                ,"children":[{"type":"tab","props":{"id":"a","label":"A"},"children":[]}]
                """, ValidationErrorCode.INVALID_PROP_VALUE, "root.props.defaultTab");
    }

    private void assertError(
            String type,
            String props,
            String children,
            ValidationErrorCode code,
            String path
    ) throws Exception {
        final var result = validate(type, props, children);

        assertFalse(result.isValid());
        assertEquals(code, result.getError().getCode());
        assertEquals(path, result.getError().getPath());
    }

    private ValidationResult validate(String type, String props, String children) throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:visual-props",
                  "properties":{
                    "width":{"kind":"int"},
                    "color":{"kind":"string"},
                    "wrong":{"kind":"bool"}
                  },
                  "actions":{},
                  "root":{"type":"%s","props":{%s}%s}
                }
                """.formatted(type, props, children.isEmpty() ? ",\"children\":[]" : children);
        final var contract = new ContractParser().parse(json);

        return validator.validate(contract);
    }
}
