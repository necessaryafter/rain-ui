package com.rainframework.ui.protocol.validation;

/**
 * Checks the size limits of properties and interaction payloads before they are parsed. The checks do not look at the
 * declared schema: that is validated separately, after the document is known to be safe to parse.
 */
public final class JsonLimitChecker {

    public ValidationResult checkProperties(String json) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public ValidationResult checkPayload(String json) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
