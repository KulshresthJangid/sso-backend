package com.sso.exception;

import org.springframework.http.HttpStatus;

public class SSOException extends RuntimeException {

    private final HttpStatus status;

    public SSOException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static SSOException notFound(String message) {
        return new SSOException(message, HttpStatus.NOT_FOUND);
    }

    public static SSOException conflict(String message) {
        return new SSOException(message, HttpStatus.CONFLICT);
    }

    public static SSOException badRequest(String message) {
        return new SSOException(message, HttpStatus.BAD_REQUEST);
    }
}
