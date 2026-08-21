package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.service.TokenRefreshService;
import com.erikferreira.stocksync.service.exceptions.InvalidQuantityException;
import com.erikferreira.stocksync.service.exceptions.InvalidSalesChannelException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceIntegrationException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class MercadoLivreAdapterTest {

    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private SalesChannelService salesChannelService;
    @Mock private TokenRefreshService tokenRefreshService;

    private MercadoLivreAdapter adapter;
    private MockRestServiceServer server;
    private SalesChannel channel;
    private MarketplaceListing listing;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadolibre.com");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MercadoLivreAdapter(builder.build(), listingRepository, salesChannelService, tokenRefreshService);
        channel = SalesChannel.builder().id(2L).type(ChannelType.MERCADO_LIVRE).active(true).build();
        listing = MarketplaceListing.builder().listingId("MLB123").salesChannel(channel).build();
    }

    @Test
    void updateStockShouldSendAuthenticatedPut() {
        mockListingToken();
        server.expect(requestTo("https://api.mercadolibre.com/items/MLB123"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().json("{\"available_quantity\":15}"))
                .andRespond(withSuccess());

        adapter.updateStock("MLB123", 15);

        server.verify();
    }

    @Test
    void updateStockShouldRejectNegativeQuantityBeforeCallingDependencies() {
        assertThatThrownBy(() -> adapter.updateStock("MLB123", -1))
                .isInstanceOf(InvalidQuantityException.class);
        verify(listingRepository, never())
                .findByListingIdAndSalesChannelType("MLB123", ChannelType.MERCADO_LIVRE);
    }

    @Test
    void updateStockShouldWrapHttpFailure() {
        mockListingToken();
        server.expect(requestTo("https://api.mercadolibre.com/items/MLB123"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> adapter.updateStock("MLB123", 10))
                .isInstanceOf(MarketplaceIntegrationException.class)
                .hasMessageContaining("Failed to update stock");
    }

    @Test
    void getCurrentStockShouldReturnAvailableQuantity() {
        mockListingToken();
        server.expect(requestTo("https://api.mercadolibre.com/items/MLB123"))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("{\"available_quantity\":27}", MediaType.APPLICATION_JSON));

        assertThat(adapter.getCurrentStock("MLB123")).isEqualTo(27);
    }

    @Test
    void getCurrentStockShouldRejectResponseWithoutQuantity() {
        mockListingToken();
        server.expect(requestTo("https://api.mercadolibre.com/items/MLB123"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.getCurrentStock("MLB123"))
                .isInstanceOf(MarketplaceIntegrationException.class)
                .hasMessageContaining("no stock information");
    }

    @Test
    void getCurrentStockShouldThrowWhenListingIsNotRegistered() {
        when(listingRepository.findByListingIdAndSalesChannelType("missing", ChannelType.MERCADO_LIVRE))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.getCurrentStock("missing"))
                .isInstanceOf(MarketplaceListingNotFoundException.class);
    }

    @Test
    void fetchNewOrdersShouldValidateChannelAndMapMercadoLivreResponse() {
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(tokenRefreshService.getValidAccessToken(2L)).thenReturn("access-token");
        server.expect(requestTo("https://api.mercadolibre.com/users/me"))
                .andRespond(withSuccess("{\"id\":999}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadolibre.com/orders/search?seller=999&order.status=paid"))
                .andRespond(withSuccess("""
                        {"results":[{"id":12345,"status":"paid","date_created":"2026-08-21T12:00:00-03:00",
                        "order_items":[{"item":{"id":"MLB123"},"quantity":2,"unit_price":19.90}]}]}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.fetchNewOrders(2L);

        assertThat(result).singleElement().satisfies(order -> {
            assertThat(order.externalOrderId()).isEqualTo("12345");
            assertThat(order.items()).singleElement().satisfies(item -> {
                assertThat(item.listingId()).isEqualTo("MLB123");
                assertThat(item.quantity()).isEqualTo(2);
            });
        });
    }

    @Test
    void fetchNewOrdersShouldRejectInactiveOrWrongChannel() {
        channel.setActive(false);
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        assertThatThrownBy(() -> adapter.fetchNewOrders(2L)).isInstanceOf(InvalidSalesChannelException.class);

        channel.setActive(true);
        channel.setType(ChannelType.SHOPEE);
        assertThatThrownBy(() -> adapter.fetchNewOrders(2L)).isInstanceOf(InvalidSalesChannelException.class);
    }

    private void mockListingToken() {
        when(listingRepository.findByListingIdAndSalesChannelType("MLB123", ChannelType.MERCADO_LIVRE))
                .thenReturn(Optional.of(listing));
        when(tokenRefreshService.getValidAccessToken(2L)).thenReturn("access-token");
    }
}
