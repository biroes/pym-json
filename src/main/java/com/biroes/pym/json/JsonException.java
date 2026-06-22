package com.biroes.pym.json;

/**
 * Unchecked exception thrown when JSON parsing, serialization, or
 * deserialization fails.
 */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
