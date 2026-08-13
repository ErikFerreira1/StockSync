package com.erikferreira.stocksync.service.exceptions;

public class MarketplaceListingNotFoundException extends RuntimeException {
    public MarketplaceListingNotFoundException(String message) {
        super(message);
    }
}
