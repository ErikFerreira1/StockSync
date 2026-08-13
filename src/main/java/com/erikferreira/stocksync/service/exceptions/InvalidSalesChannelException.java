package com.erikferreira.stocksync.service.exceptions;

public class InvalidSalesChannelException extends RuntimeException {
    public InvalidSalesChannelException(String message) {
        super(message);
    }
}
