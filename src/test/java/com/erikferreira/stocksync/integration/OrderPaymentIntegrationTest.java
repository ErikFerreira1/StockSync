package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.entity.enums.*;
import com.erikferreira.stocksync.repository.OrderRepository;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.service.*;
import com.erikferreira.stocksync.service.exceptions.*;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderPaymentIntegrationTest extends PostgreSQLIntegrationTest {
    @MockitoBean MarketplaceIntegrationPort marketplace;
    @Autowired ProductService products;
    @Autowired SalesChannelService channels;
    @Autowired OrderService orders;
    @Autowired InventoryService inventory;
    @Autowired StockMovementService movements;
    @Autowired StockMovementRepository movementRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired MockMvc mvc;
    Long productId, channelId;

    @BeforeEach void setUp() {
        String suffix = UUID.randomUUID().toString();
        productId = products.insert(new ProductInsertDTO("PAY-" + suffix, "Payment product", null, BigDecimal.TEN, 10, 0)).id();
        channelId = channels.insert(new SalesChannelRequestDTO("Pay-" + suffix, ChannelType.OTHER, null)).id();
    }

    OrderResponseDTO pending(int quantity) {
        return orders.insert(new OrderInsertDTO(UUID.randomUUID().toString(), channelId, Instant.now(),
                List.of(new OrderItemInsertDTO(productId, quantity, BigDecimal.TEN))));
    }
    long movementCount(Long id, MovementType type) {
        return movementRepository.findByProductId(productId, Pageable.unpaged()).stream()
                .filter(m -> Objects.equals(m.getOriginId(), id) && m.getType() == type).count();
    }

    @Test void pendingOrderMustNotReserveOrDeductAndCancellationMustNotIncreaseStock() {
        var created = pending(20);
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.stockDeducted()).isFalse();
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        assertThat(movementCount(created.id(), MovementType.SALE)).isZero();
        orders.cancelOrder(created.id()); orders.cancelOrder(created.id());
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        assertThat(movementCount(created.id(), MovementType.CANCELLATION)).isZero();
    }

    @Test void paymentAndPaidCancellationMustMoveStockExactlyOnce() {
        var created = pending(3);
        assertThat(orders.confirmPayment(created.id()).stockDeducted()).isTrue();
        orders.confirmPayment(created.id());
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(7);
        assertThat(movementCount(created.id(), MovementType.SALE)).isEqualTo(1);
        var canceled = orders.cancelOrder(created.id()); orders.cancelOrder(created.id());
        assertThat(canceled.stockDeducted()).isFalse();
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        assertThat(movementCount(created.id(), MovementType.CANCELLATION)).isEqualTo(1);
    }

    @Test void insufficientStockMustKeepPendingAndRollbackEveryItem() {
        var empty = products.insert(new ProductInsertDTO("EMPTY-" + UUID.randomUUID(), "Empty product", null, BigDecimal.TEN, 0, 0));
        var created = orders.insert(new OrderInsertDTO(UUID.randomUUID().toString(), channelId, Instant.now(), List.of(
                new OrderItemInsertDTO(productId, 3, BigDecimal.TEN), new OrderItemInsertDTO(empty.id(), 1, BigDecimal.TEN))));
        assertThatThrownBy(() -> orders.confirmPayment(created.id())).isInstanceOf(InsufficientStockException.class);
        assertThat(orders.findById(created.id()).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(orders.findById(created.id()).stockDeducted()).isFalse();
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        assertThat(inventory.getAvailableQuantity(empty.id())).isZero();
        assertThat(movementCount(created.id(), MovementType.SALE)).isZero();
        orders.cancelOrder(created.id());
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
    }

    @Test void repeatedProductLinesMustUseTheUpdatedBalanceWithinTheSamePayment() {
        var created = orders.insert(new OrderInsertDTO(UUID.randomUUID().toString(), channelId, Instant.now(), List.of(
                new OrderItemInsertDTO(productId, 4, BigDecimal.TEN), new OrderItemInsertDTO(productId, 7, BigDecimal.TEN))));
        assertThatThrownBy(() -> orders.confirmPayment(created.id())).isInstanceOf(InsufficientStockException.class);
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        assertThat(orders.findById(created.id()).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(movementCount(created.id(), MovementType.SALE)).isZero();
    }

    @Test void legacyPendingOrderWithExistingSaleMustNotDeductAgain() {
        var created = pending(3);
        movements.registerMovement(new StockMovementInsertDTO(productId, 3, MovementType.SALE, OriginType.ORDER, created.id(), "Legacy sale"));
        var legacy = orderRepository.findById(created.id()).orElseThrow();
        legacy.setStockDeducted(true); orderRepository.saveAndFlush(legacy);
        orders.confirmPayment(created.id());
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(7);
        assertThat(movementCount(created.id(), MovementType.SALE)).isEqualTo(1);
        orders.cancelOrder(created.id());
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
    }

    @Test void concurrentPaymentConfirmationsMustDeductOnlyOnce() throws Exception {
        var created = pending(3);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> operations = new ArrayList<>();
            for (int i = 0; i < 5; i++) operations.add(executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return orders.confirmPayment(created.id());
            }));
            start.countDown();
            for (var operation : operations) operation.get(15, TimeUnit.SECONDS);
            assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(7);
            assertThat(movementCount(created.id(), MovementType.SALE)).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void competingOrdersMustNotOversell() throws Exception {
        var first = pending(7); var second = pending(7);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> operations = new ArrayList<>();
            for (var order : List.of(first, second)) operations.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try { orders.confirmPayment(order.id()); return true; }
                catch (InsufficientStockException exception) { return false; }
            }));
            start.countDown();
            assertThat(List.of(operations.get(0).get(15, TimeUnit.SECONDS), operations.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(3);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void paymentRacingCancellationMustLeaveCanceledOrderAndOriginalBalance() throws Exception {
        var created = pending(3);
        var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var payment = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try { return orders.confirmPayment(created.id()); }
                catch (InvalidOrderStatusException exception) { return null; }
            });
            var cancel = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return orders.cancelOrder(created.id()); });
            start.countDown(); payment.get(15, TimeUnit.SECONDS); cancel.get(15, TimeUnit.SECONDS);
            assertThat(orders.findById(created.id()).status()).isEqualTo(OrderStatus.CANCELED);
            assertThat(orders.findById(created.id()).stockDeducted()).isFalse();
            assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(10);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void paymentEndpointMustEnforcePermissionsAndReturnPaidState() throws Exception {
        var created = pending(3);
        mvc.perform(patch("/orders/{id}/confirm-payment", created.id()).with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/orders/{id}/confirm-payment", created.id())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/orders/{id}/confirm-payment", created.id()).with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.stockDeducted").value(true));
        assertThat(inventory.getAvailableQuantity(productId)).isEqualTo(7);
    }
}
