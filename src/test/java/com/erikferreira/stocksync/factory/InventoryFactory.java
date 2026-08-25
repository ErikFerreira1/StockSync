package com.erikferreira.stocksync.factory;

import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;

import java.time.Instant;

public class InventoryFactory {

    public static Inventory createInventory (Product product) {
        return Inventory.builder()
                .product(product)
                .availableQuantity(50)
                .minQuantity(1)
                .updatedAt(Instant.now())
                .build();
    }
}
