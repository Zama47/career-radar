package com.careerradar.common.error;

/**
 * Thrown when a request is syntactically valid but carries an unsupported value.
 * Mapped to RFC 7807 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
