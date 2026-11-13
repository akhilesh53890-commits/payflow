package com.payflow.common.exception;

public class ConflictException extends ApiException {
    public ConflictException(String errorCode, String message) {
        super(409, errorCode, message);
    }

    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }
}

