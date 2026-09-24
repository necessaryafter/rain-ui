package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.ParseException;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.protocol.validation.PropertiesValidatorTest.assertFailure;
import static com.rainframework.ui.protocol.validation.PropertiesValidatorTest.assertValid;
import static com.rainframework.ui.protocol.validation.PropertiesValidatorTest.contract;

class PayloadValidatorTest {
    private static final String FIRE = "b2ee0ef0cf4fd36e0d0426ec06dbe1db77735997c628f1aafd6eb4c14b0b567a";

    private final PayloadValidator validator = new PayloadValidator();

    @Test
    void acceptsAPayloadThatMatchesTheActionSchema() throws Exception {
        assertValid(validator.validate(contract("double"), "test:bid", """
                { "amount": 12.5, "listingId": "a1" }
                """));
    }

    @Test
    void acceptsAnIntegerWhereADoubleIsDeclared() throws Exception {
        assertValid(validator.validate(contract("double"), "test:bid", """
                { "amount": 12, "listingId": "a1" }
                """));
    }

    @Test
    void acceptsAnOmittedOptionalField() throws Exception {
        assertValid(validator.validate(contract("payload-optional"), "test:act", """
                { "id": "x" }
                """));
    }

    @Test
    void rejectsAMissingRequiredField() throws Exception {
        assertFailure(validator.validate(contract("double"), "test:bid", """
                { "amount": 12.5 }
                """), ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, "listingId");
    }

    @Test
    void rejectsAnUndeclaredField() throws Exception {
        assertFailure(validator.validate(contract("double"), "test:bid", """
                { "amount": 12.5, "listingId": "a1", "price": 1 }
                """), ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, "price");
    }

    @Test
    void rejectsAFieldOfTheWrongType() throws Exception {
        assertFailure(validator.validate(contract("double"), "test:bid", """
                { "amount": "12.5", "listingId": "a1" }
                """), ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, "amount");
    }

    @Test
    void rejectsADecimalWhereAnIntIsDeclared() throws Exception {
        assertFailure(validator.validate(inlineContract(), "test:tax", """
                { "step": 1.5 }
                """), ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, "step");
    }

    @Test
    void rejectsAnUndeclaredAction() throws Exception {
        assertFailure(validator.validate(contract("double"), "test:steal", """
                { "amount": 12.5, "listingId": "a1" }
                """), ValidationErrorCode.UNDECLARED_ACTION, "actionId");
    }

    @Test
    void checksThePayloadSizeBeforeTheSchema() throws Exception {
        final var payload = "{\"listingId\": \"" + "a".repeat(8200) + "\"}";

        assertFailure(
                validator.validate(contract("double"), "test:bid", payload),
                ValidationErrorCode.LIMIT_EXCEEDED,
                "root");
    }

    @Test
    void acceptsADeclaredAssetName() throws Exception {
        assertValid(validator.validate(inlineContract(), "test:pick", """
                { "icon": "fire" }
                """));
    }

    @Test
    void rejectsAnUndeclaredAssetName() throws Exception {
        assertFailure(validator.validate(inlineContract(), "test:pick", """
                { "icon": "lava" }
                """), ValidationErrorCode.UNDECLARED_ASSET, "icon");
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertFailure(validator.validate(contract("double"), "test:bid", "{\"amount\":"),
                ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH,
                "root");
    }

    private static Contract inlineContract() throws ParseException {
        return new ContractParser().parse("""
                {
                  "schemaVersion": 0,
                  "id": "test:payload",
                  "properties": {},
                  "actions": {
                    "test:tax": { "kind": "object", "fields": { "step": { "kind": "int" } } },
                    "test:pick": { "kind": "object", "fields": { "icon": { "kind": "asset" } } }
                  },
                  "assets": { "%s": { "type": "image/png", "bytes": 512, "width": 16, "height": 16 } },
                  "assetNames": { "fire": "%s" },
                  "root": { "type": "text", "props": { "value": "x" }, "children": [] }
                }
                """.formatted(FIRE, FIRE));
    }
}
