package com.rainframework.ui.protocol.payload;

/**
 * Thrown when plugin code reads a payload field with the wrong accessor, reads an undeclared field, or requires a
 * field that is absent. The payload itself was already validated, so this is a bug in the caller, not bad input.
 */
public final class PayloadTypeException extends RuntimeException {

    public PayloadTypeException(String message) {
        super(message);
    }
}
