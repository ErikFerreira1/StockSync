package com.erikferreira.stocksync.event;

public record ProductStatusChangedEvent(Long productId, boolean active) {
}
