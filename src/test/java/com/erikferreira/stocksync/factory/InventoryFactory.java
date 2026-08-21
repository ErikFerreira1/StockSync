package com.erikferreira.stocksync.factory;

import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;

import java.time.LocalDateTime;

public class InventoryFactory {

    public static Inventory createInventory (Product product) {
        return Inventory.builder()
                .product(product)
                .availableQuantity(50)
                .minQuantity(1)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
