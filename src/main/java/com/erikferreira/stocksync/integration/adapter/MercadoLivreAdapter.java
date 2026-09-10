package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderItemDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreItemResponseDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreItemStatusUpdateDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreItemUpdateDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreOrderItemResponseDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreOrderResponseDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreOrdersSearchResponseDTO;
import com.erikferreira.stocksync.integration.dto.mercadoLivre.MercadoLivreUserResponseDTO;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.service.TokenRefreshService;
import com.erikferreira.stocksync.service.exceptions.InvalidQuantityException;
import com.erikferreira.stocksync.service.exceptions.InvalidSalesChannelException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceIntegrationException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MercadoLivreAdapter implements MarketplaceIntegrationPort {

    private final RestClient mercadoLivreRestClient;
    private final MarketplaceListingRepository marketplaceListingRepository;
    private final SalesChannelService salesChannelService;
    private final TokenRefreshService tokenRefreshService;

    @Override
    public List<ExternalOrderDTO> fetchNewOrders(Long salesChannelId) {
        SalesChannel channel = getActiveMercadoLivreChannel(salesChannelId);
        String accessToken = tokenRefreshService.getValidAccessToken(channel.getId());
        Long sellerId = getSellerId(accessToken, salesChannelId);
        List<MercadoLivreOrderResponseDTO> orders = getPaidOrders(accessToken, sellerId, salesChannelId);

        return orders.stream()
                .map(this::toExternalOrder)
                .toList();
    }

    @Override
    public void updateStock(String listingId, Integer quantity) {
        validateQuantity(quantity);
        String accessToken = getAccessTokenForListing(listingId);

        try {
            mercadoLivreRestClient
                    .put()
                    .uri("/items/{itemId}", listingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new MercadoLivreItemUpdateDTO(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new MarketplaceIntegrationException(
                    "Failed to update stock for Mercado Livre listing id: " + listingId,
                    ex
            );
        }
    }

    @Override
    public void updateListingStatus(String listingId, ListingStatus status, Integer availableQuantity) {
        validateListingStatusUpdate(status, availableQuantity);
        String accessToken = getAccessTokenForListing(listingId);

        try {
            mercadoLivreRestClient
                    .put()
                    .uri("/items/{itemId}", listingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new MercadoLivreItemStatusUpdateDTO(
                            status.name().toLowerCase(Locale.ROOT),
                            availableQuantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new MarketplaceIntegrationException(
                    "Failed to update status for Mercado Livre listing id: " + listingId,
                    ex
            );
        }
    }

    @Override
    public Integer getCurrentStock(String listingId) {
        String accessToken = getAccessTokenForListing(listingId);
        MercadoLivreItemResponseDTO response;
        try {
            response = mercadoLivreRestClient
                    .get()
                    .uri("/items/{itemId}", listingId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MercadoLivreItemResponseDTO.class);
        } catch (RestClientException ex) {
            throw new MarketplaceIntegrationException(
                    "Failed to retrieve stock for Mercado Livre listing id: " + listingId,
                    ex
            );
        }

        return getAvailableQuantity(response, listingId);
    }

    // helpers

    private SalesChannel getActiveMercadoLivreChannel(Long salesChannelId) {
        SalesChannel channel = salesChannelService.getSalesChannelEntityById(salesChannelId);

        if (channel.getType() != ChannelType.MERCADO_LIVRE) {
            throw new InvalidSalesChannelException(
                    "Sales channel " + salesChannelId + " is not a Mercado Livre channel"
            );
        }
        if (!channel.isActive()) {
            throw new InvalidSalesChannelException("Sales channel " + salesChannelId + " is inactive");
        }
        return channel;
    }

    private Long getSellerId(String accessToken, Long salesChannelId) {
        MercadoLivreUserResponseDTO response;
        try {
            response = mercadoLivreRestClient.get()
                    .uri("/users/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MercadoLivreUserResponseDTO.class);
        } catch (RestClientException ex) {
            throw new MarketplaceIntegrationException(
                    "Failed to retrieve Mercado Livre user for sales channel id: " + salesChannelId, ex
            );
        }

        if (response == null || response.id() == null) {
            throw new MarketplaceIntegrationException("Mercado Livre response did not include the seller id");
        }
        return response.id();
    }

    private List<MercadoLivreOrderResponseDTO> getPaidOrders(
            String accessToken, Long sellerId, Long salesChannelId) {
        MercadoLivreOrdersSearchResponseDTO response;
        try {
            response = mercadoLivreRestClient.get()
                    .uri("/orders/search?seller={sellerId}&order.status=paid", sellerId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MercadoLivreOrdersSearchResponseDTO.class);
        } catch (RestClientException ex) {
            throw new MarketplaceIntegrationException(
                    "Failed to retrieve paid orders for Mercado Livre sales channel id: " + salesChannelId, ex
            );
        }

        if (response == null) {
            throw new MarketplaceIntegrationException("Mercado Livre returned an empty orders-search response");
        }
        return response.results() == null ? List.of() : response.results();
    }

    private Integer getAvailableQuantity(MercadoLivreItemResponseDTO response, String listingId) {
        if (response == null || response.availableQuantity() == null) {
            throw new MarketplaceIntegrationException(
                    "Mercado Livre returned no stock information for listing id: " + listingId
            );
        }
        return response.availableQuantity();
    }

    private void validateListingStatusUpdate(ListingStatus status, Integer availableQuantity) {
        if (status != ListingStatus.ACTIVE && status != ListingStatus.PAUSED) {
            throw new IllegalArgumentException("Only ACTIVE and PAUSED listing status updates are supported");
        }
        if (availableQuantity != null) {
            validateQuantity(availableQuantity);
        }
        if (status == ListingStatus.PAUSED && availableQuantity != null) {
            throw new IllegalArgumentException("Available quantity must not be sent when pausing a listing");
        }
        if (status == ListingStatus.ACTIVE && availableQuantity == null) {
            throw new IllegalArgumentException("Available quantity is required when activating a listing");
        }
    }

    private String getAccessTokenForListing(String listingId) {
        return getAccessToken(findMercadoLivreListing(listingId));
    }

    private String getAccessToken(MarketplaceListing listing) {
        return tokenRefreshService.getValidAccessToken(listing.getSalesChannel().getId());
    }

    private MarketplaceListing findMercadoLivreListing(String listingId) {
        return marketplaceListingRepository
                .findByListingIdAndSalesChannelType(listingId, ChannelType.MERCADO_LIVRE)
                .orElseThrow(() -> new MarketplaceListingNotFoundException(
                        "No Mercado Livre listing is registered with id: " + listingId
                ));
    }


    private ExternalOrderDTO toExternalOrder(MercadoLivreOrderResponseDTO order) {
        validateOrderResponse(order);
        List<ExternalOrderItemDTO> orderItems = order.orderItems().stream()
                .map(this::toExternalOrderItem)
                .toList();

        return new ExternalOrderDTO(
                order.id().toString(),
                order.dateCreated().toInstant(),
                order.status(),
                orderItems
        );
    }

    private ExternalOrderItemDTO toExternalOrderItem(MercadoLivreOrderItemResponseDTO item) {
        validateOrderItemResponse(item);

        return new ExternalOrderItemDTO(
                item.item().id(),
                item.quantity(),
                item.unitPrice()
        );
    }

    private void validateOrderResponse(MercadoLivreOrderResponseDTO order) {
        if (order == null || order.id() == null || order.dateCreated() == null
                || order.status() == null || order.orderItems() == null) {
            throw new MarketplaceIntegrationException(
                    "Mercado Livre returned an order with missing required fields"
            );
        }
    }

    private void validateOrderItemResponse(MercadoLivreOrderItemResponseDTO item) {
        if (item == null || item.item() == null || item.item().id() == null
                || item.quantity() == null || item.unitPrice() == null) {
            throw new MarketplaceIntegrationException(
                    "Mercado Livre returned an order item with missing required fields"
            );
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new InvalidQuantityException("Quantity cannot be null or less than zero");
        }
    }




}
