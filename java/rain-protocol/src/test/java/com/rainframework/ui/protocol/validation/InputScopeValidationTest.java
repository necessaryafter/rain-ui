package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputScopeValidationTest {
    private final ContractValidator validator = new ContractValidator();

    @Test
    void rejectsAnInputReferenceToAnotherKeyedList() throws Exception {
        final var result = validate("");

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.UNDECLARED_INPUT, result.getError().getCode());
    }

    @Test
    void allowsAKeyedListToReadAScreenScopedInput() throws Exception {
        final var screenInput = """
                ,{"type":"input","props":{"id":"query"},"children":[]}
                """;

        assertTrue(validate(screenInput).isValid());
    }

    private ValidationResult validate(String screenInput) throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:input-scope",
                  "properties":{
                    "first":{"kind":"list","of":{"kind":"object","fields":{"id":{"kind":"string"}}}},
                    "second":{"kind":"list","of":{"kind":"object","fields":{"id":{"kind":"string"}}}}
                  },
                  "actions":{"test:send":{"kind":"object","fields":{"query":{"kind":"string"}}}},
                  "root":{"type":"box","props":{},"children":[
                    {"type":"list","props":{"source":{"$bind":"first"}},"children":[
                      {"type":"box","key":{"$bind":"id"},"props":{},"children":[
                        {"type":"button","props":{"action":"test:send","payload":{
                          "query":{"$input":"query"}
                        }},"children":[]}
                      ]}
                    ]},
                    {"type":"list","props":{"source":{"$bind":"second"}},"children":[
                      {"type":"box","key":{"$bind":"id"},"props":{},"children":[
                        {"type":"input","props":{"id":"query"},"children":[]}
                      ]}
                    ]}%s
                  ]}
                }
                """.formatted(screenInput);
        final var contract = new ContractParser().parse(json);

        return validator.validate(contract);
    }
}
