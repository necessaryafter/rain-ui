package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rainframework.ui.protocol.Contract;

/** Validates the properties a server sends against the screen's declared properties, limits first. */
public final class PropertiesValidator {
    private final JsonLimitChecker limitChecker = new JsonLimitChecker();

    public ValidationResult validate(Contract contract, String json) {
        final var limits = limitChecker.checkProperties(json);
        if (!limits.isValid()) {
            return limits;
        }

        final var checker = new ValueChecker(
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                true,
                contract.assetNames().keySet());

        try {
            final var root = ValueChecker.MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return checker.fail("");
            }

            return checker.checkFields(root, contract.properties(), "");
        } catch (JsonProcessingException e) {
            return checker.fail("");
        }
    }
}
