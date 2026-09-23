package com.rainframework.ui.protocol;

import com.rainframework.ui.protocol.validation.ValidationErrorCode;
import lombok.Getter;

@Getter
public final class ParseException extends Exception {
    private final ValidationErrorCode errorCode;
    private final String path;

    public ParseException(ValidationErrorCode errorCode, String path) {
        super(errorCode + " at " + path);
        this.errorCode = errorCode;
        this.path = path;
    }
}
