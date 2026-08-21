package com.erikferreira.stocksync.factory;

import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;

import java.math.BigDecimal;

public class ProductFactory {

    public static Product createProduct() {
        return Product.builder()
                .id(1L)
                .sku("SKU-001")
                .name("product test")
                .basePrice(BigDecimal.TEN)
                .active(true)
                .build();
    }

    public static Product createProductWithInventory() {
        Product product = ProductFactory.createProduct();

        Inventory inventory = InventoryFactory.createInventory(product);
        product.setInventory(inventory);

        return product;
    }
}
