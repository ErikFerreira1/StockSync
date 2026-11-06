package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.entity.*;
import com.erikferreira.stocksync.entity.enums.*;
import com.erikferreira.stocksync.repository.*;
import com.erikferreira.stocksync.service.*;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SyncEventSearchIntegrationTest extends PostgreSQLIntegrationTest {
    @MockitoBean MarketplaceIntegrationPort marketplace;
    @Autowired MockMvc mvc;
    @Autowired ProductService products;
    @Autowired SalesChannelService channels;
    @Autowired OrderService orders;
    @Autowired ProductRepository productRepository;
    @Autowired SalesChannelRepository channelRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired SyncEventRepository repository;
    Product product;
    SalesChannel channel;
    Order order;
    SyncEvent productFailure, productSuccess, orderFailure, orderSuccess;

    @BeforeEach void setUp() {
        String suffix = UUID.randomUUID().toString();
        var p = products.insert(new ProductInsertDTO("AUDIT-" + suffix, "Audit product", null, BigDecimal.TEN, 20, 0));
        var c = channels.insert(new SalesChannelRequestDTO("Audit-" + suffix, ChannelType.OTHER, null));
        var other = channels.insert(new SalesChannelRequestDTO("Other-" + suffix, ChannelType.OTHER, null));
        var o = orders.insert(new OrderInsertDTO("AUDIT-ORDER-" + suffix, c.id(), Instant.now(),
                List.of(new OrderItemInsertDTO(p.id(), 1, BigDecimal.TEN))));
        product = productRepository.findById(p.id()).orElseThrow();
        channel = channelRepository.findById(c.id()).orElseThrow();
        order = orderRepository.findById(o.id()).orElseThrow();
        productFailure = save(product, channel, null, null, SyncStatus.FAILURE, 0);
        productSuccess = save(product, channel, null, null, SyncStatus.SUCCESS, 1);
        orderFailure = save(null, channel, null, "EXT-FAILED", SyncStatus.FAILURE, 2);
        orderSuccess = save(null, channel, order, "EXT-SUCCESS", SyncStatus.SUCCESS, 3);
        save(product, channelRepository.findById(other.id()).orElseThrow(), null, null, SyncStatus.FAILURE, 4);
    }

    SyncEvent save(Product product, SalesChannel channel, Order order, String externalId, SyncStatus status, int seconds) {
        return repository.saveAndFlush(SyncEvent.builder().product(product).salesChannel(channel).order(order)
                .externalOrderId(externalId).timestamp(Instant.parse("2026-11-06T12:00:00Z").plusSeconds(seconds))
                .status(status).errorMessage(status == SyncStatus.FAILURE ? "secret-token must never be exposed" : null).build());
    }

    @Test void viewerCanReadPaginatedHistoryWithStableDefaultSortAndSafeErrors() throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].id").value(orderSuccess.getId()))
                .andExpect(jsonPath("$.content[1].id").value(orderFailure.getId()))
                .andExpect(jsonPath("$.content[1].errorMessage").value("Synchronization failed"))
                .andExpect(content().string(not(containsString("secret-token"))));
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(productSuccess.getId()))
                .andExpect(jsonPath("$.content[1].id").value(productFailure.getId()));
    }

    @Test void filtersShouldCombineChannelProductAndStatus() throws Exception {
        mvc.perform(get("/sync-events").with(user("operator").roles("OPERATOR"))
                .param("salesChannelId", channel.getId().toString()).param("productId", product.getId().toString()).param("status", "FAILURE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productFailure.getId()));
    }

    @Test void orderScopeMustIncludeImportFailuresThatDoNotYetHaveALocalOrder() throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("scope", "ORDER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].externalOrderId").value("EXT-FAILED"))
                .andExpect(jsonPath("$.content[1].orderId").isEmpty());
    }

    @Test void productScopeMustExcludeOrderEvents() throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("scope", "PRODUCT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void localAndExternalOrderFiltersMustWorkIndependently() throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("orderId", order.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderSuccess.getId()));
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("externalOrderId", "EXT-FAILED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderFailure.getId()));
    }

    @ParameterizedTest
    @CsvSource({"productId,-1", "salesChannelId,0", "orderId,-3", "scope,UNKNOWN", "status,FAILED"})
    void invalidFiltersShouldReturnBadRequest(String key, String value) throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER")).param(key, value)).andExpect(status().isBadRequest());
    }

    @Test void anonymousAccessShouldBeRejected() throws Exception {
        mvc.perform(get("/sync-events")).andExpect(status().isUnauthorized());
    }

    @Test void externalOrderSearchMustBeExactAndRejectOversizedIdentifiers() throws Exception {
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("salesChannelId", channel.getId().toString()).param("externalOrderId", "EXT-FAIL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/sync-events").with(user("viewer").roles("VIEWER"))
                .param("externalOrderId", "X".repeat(256))).andExpect(status().isBadRequest());
    }
}
