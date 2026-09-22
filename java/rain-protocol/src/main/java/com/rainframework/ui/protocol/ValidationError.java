package com.rainframework.ui.protocol;

public final class ValidationError {
  private final ValidationErrorCode code;
  private final String path;

  public ValidationError(ValidationErrorCode code, String path) {
    this.code = code;
    this.path = path;
  }

  public ValidationErrorCode getCode() {
    return code;
  }

  public String getPath() {
    return path;
  }
}
