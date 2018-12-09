package com.eve.marketwatch.exceptions;

public class UnknownUserException extends Throwable {
    public UnknownUserException(int characterId) {
        super("No user exists for characterId " + characterId);
    }
}
