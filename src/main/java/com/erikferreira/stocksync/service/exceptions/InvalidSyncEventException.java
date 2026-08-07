package com.erikferreira.stocksync.service.exceptions;

public class InvalidSyncEventException extends RuntimeException {
    public InvalidSyncEventException(String message) {
        super(message);
    }
}
