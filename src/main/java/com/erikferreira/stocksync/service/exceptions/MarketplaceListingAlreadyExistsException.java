package com.erikferreira.stocksync.service.exceptions;

public class MarketplaceListingAlreadyExistsException extends RuntimeException {

    public MarketplaceListingAlreadyExistsException(String message) {
        super(message);
    }
}
