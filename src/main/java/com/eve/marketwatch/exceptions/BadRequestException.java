package com.eve.marketwatch.exceptions;

public class BadRequestException extends Throwable {
    public BadRequestException(String message) {
        super(message);
    }
}
