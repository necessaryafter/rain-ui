package com.rainframework.ui.server;

import com.rainframework.ui.protocol.validation.ValidationError;
import lombok.Getter;

/** Thrown on the server when properties do not match the screen's contract, before anything is sent. */
@Getter
public final class InvalidPropertiesException extends RuntimeException {
    private final ValidationError error;

    public InvalidPropertiesException(String screenId, ValidationError error) {
        super("Properties for " + screenId + " are invalid: " + error.getCode() + " at " + error.getPath());
        this.error = error;
    }
}
