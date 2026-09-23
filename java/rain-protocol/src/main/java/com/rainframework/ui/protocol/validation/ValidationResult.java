package com.rainframework.ui.protocol.validation;

public final class ValidationResult {
    private final boolean valid;
    private final ValidationError error;

    private ValidationResult(boolean valid, ValidationError error) {
        this.valid = valid;
        this.error = error;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(ValidationErrorCode code, String path) {
        return new ValidationResult(false, new ValidationError(code, path));
    }

    public boolean isValid() {
        return valid;
    }

    public ValidationError getError() {
        return error;
    }
}