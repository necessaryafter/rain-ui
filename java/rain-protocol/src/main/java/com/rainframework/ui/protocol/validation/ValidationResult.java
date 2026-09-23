package com.rainframework.ui.protocol.validation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationResult {
    private final boolean valid;
    private final ValidationError error;

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(ValidationErrorCode code, String path) {
        return new ValidationResult(false, new ValidationError(code, path));
    }
}
