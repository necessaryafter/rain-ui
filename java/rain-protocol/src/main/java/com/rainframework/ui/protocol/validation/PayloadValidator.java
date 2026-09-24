package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rainframework.ui.protocol.Contract;

/** Validates an interaction's payload against its action's declared schema, limits first. */
public final class PayloadValidator {
    private final JsonLimitChecker limitChecker = new JsonLimitChecker();

    public ValidationResult validate(Contract contract, String actionId, String json) {
        final var schema = contract.actions().get(actionId);
        if (schema == null) {
            return ValidationResult.fail(ValidationErrorCode.UNDECLARED_ACTION, "actionId");
        }

        final var limits = limitChecker.checkPayload(json);
        if (!limits.isValid()) {
            return limits;
        }

        final var checker = new ValueChecker(
                ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH,
                false,
                contract.assetNames().keySet());

        try {
            return checker.check(ValueChecker.MAPPER.readTree(json), schema, "");
        } catch (JsonProcessingException e) {
            return checker.fail("");
        }
    }
}
