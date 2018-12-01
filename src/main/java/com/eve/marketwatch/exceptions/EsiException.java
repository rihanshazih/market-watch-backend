package com.eve.marketwatch.exceptions;

import com.eve.marketwatch.model.esi.EsiError;

public class EsiException extends Throwable {
    private final EsiError error;

    public EsiException(EsiError error) {
        this.error = error;
    }

    public EsiError getError() {
        return error;
    }
}
