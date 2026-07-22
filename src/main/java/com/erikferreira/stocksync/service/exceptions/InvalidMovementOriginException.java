package com.erikferreira.stocksync.service.exceptions;

public class InvalidMovementOriginException extends RuntimeException {
    public InvalidMovementOriginException(String message) {
        super(message);
    }
}
