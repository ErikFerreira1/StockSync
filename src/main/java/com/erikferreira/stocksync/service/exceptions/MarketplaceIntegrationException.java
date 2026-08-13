package com.erikferreira.stocksync.service.exceptions;

public class MarketplaceIntegrationException extends RuntimeException {
    public MarketplaceIntegrationException(String message) {
        super(message);
    }

    public MarketplaceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
