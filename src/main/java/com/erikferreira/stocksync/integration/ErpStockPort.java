package com.erikferreira.stocksync.integration;

public interface ErpStockPort {
    Integer getCurrentStock(String sku);
    void updateStock(String sku, Integer quantity);
}
