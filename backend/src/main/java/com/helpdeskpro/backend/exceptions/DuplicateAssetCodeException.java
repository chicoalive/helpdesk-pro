package com.helpdeskpro.backend.exceptions;

public class DuplicateAssetCodeException extends RuntimeException {
    public DuplicateAssetCodeException(String message) {
        super(message);
    }
}
