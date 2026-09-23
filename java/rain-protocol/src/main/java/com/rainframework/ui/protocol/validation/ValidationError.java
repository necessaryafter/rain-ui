package com.rainframework.ui.protocol.validation;

import lombok.Value;

@Value
public class ValidationError {
    ValidationErrorCode code;
    String path;
}
