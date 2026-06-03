package com.unisphere.backend.common.exception;

public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
