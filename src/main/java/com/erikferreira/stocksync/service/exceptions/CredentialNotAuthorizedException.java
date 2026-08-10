package com.erikferreira.stocksync.service.exceptions;

public class CredentialNotAuthorizedException extends RuntimeException {
    public CredentialNotAuthorizedException(String message) {
        super(message);
    }
}
