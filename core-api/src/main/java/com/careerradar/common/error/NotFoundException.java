package com.careerradar.common.error;

/**
 * Thrown when a request references a resource that does not exist. Mapped to RFC 7807 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
