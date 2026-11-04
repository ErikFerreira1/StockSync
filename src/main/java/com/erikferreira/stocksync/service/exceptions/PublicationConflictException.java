package com.erikferreira.stocksync.service.exceptions;

public class PublicationConflictException extends RuntimeException {
    public PublicationConflictException(String message) { super(message); }
}
