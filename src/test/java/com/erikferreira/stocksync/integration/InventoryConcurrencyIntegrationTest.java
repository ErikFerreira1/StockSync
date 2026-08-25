package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.inventory.StockRequestDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.service.InventoryService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.test.inventory-concurrency=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryConcurrencyIntegrationTest extends PostgreSQLIntegrationTest {

    private static final int CONCURRENT_DECREASES = 10;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void concurrentDecreasesShouldNotLoseStockUpdates() throws Exception {
        var product = productService.insert(new ProductInsertDTO(
                "SKU-CONCURRENCY-" + System.nanoTime(),
                "Concurrency product",
                null,
                BigDecimal.TEN,
                100,
                0
        ));

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_DECREASES);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_DECREASES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> operations = new ArrayList<>();

        try {
            for (int index = 0; index < CONCURRENT_DECREASES; index++) {
                operations.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent operations were not released in time");
                    }

                    inventoryService.decreaseStock(new StockRequestDTO(product.id(), 1));
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> operation : operations) {
                operation.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(90);
    }
}
