package com.erikferreira.stocksync.config.security.exceptions;

import java.security.GeneralSecurityException;

public class CryptoException extends RuntimeException {
    public CryptoException(String message, GeneralSecurityException e) {
        super(message);
    }
}
