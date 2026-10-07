package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedKeyValidationTest {
    private static final String CONTRACT = """
            {
              "schemaVersion":0,
              "id":"test:keyed-list",
              "properties":{
                "items":{"kind":"list","of":{"kind":"object","fields":{
                  "id":{"kind":"string","optional":true}
                }}}
              },
              "actions":{},
              "root":{"type":"list","props":{"source":{"$bind":"items"}},"children":[
                {"type":"box","key":{"$bind":"id"},"props":{},"children":[]}
              ]}
            }
            """;

    private final PropertiesValidator validator = new PropertiesValidator();

    @Test
    void acceptsDistinctKeys() throws Exception {
        assertTrue(validate("{\"items\":[{\"id\":\"a\"},{\"id\":\"b\"}]}").isValid());
    }

    @Test
    void rejectsDuplicateResolvedKeysBeforePublishing() throws Exception {
        final var result = validate("{\"items\":[{\"id\":\"a\"},{\"id\":\"a\"}]}");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.DUPLICATE_KEY, result.getError().getCode());
        assertEquals("root.children[0].key", result.getError().getPath());
    }

    @Test
    void rejectsAnAbsentOptionalKey() throws Exception {
        final var result = validate("{\"items\":[{}]}");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INVALID_KEY, result.getError().getCode());
        assertEquals("root.children[0].key", result.getError().getPath());
    }

    private ValidationResult validate(String properties) throws Exception {
        final var contract = new ContractParser().parse(CONTRACT);

        return validator.validate(contract, properties);
    }
}
