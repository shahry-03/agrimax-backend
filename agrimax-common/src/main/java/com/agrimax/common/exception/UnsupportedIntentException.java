package com.agrimax.common.exception;

public class UnsupportedIntentException extends RuntimeException {
    public UnsupportedIntentException(String intent) {
        super("Unsupported voice intent: " + intent);
    }
}
