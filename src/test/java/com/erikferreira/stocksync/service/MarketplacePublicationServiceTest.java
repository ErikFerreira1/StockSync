package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.entity.*;
import com.erikferreira.stocksync.entity.enums.*;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort.PublishedListing;
import com.erikferreira.stocksync.service.exceptions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplacePublicationServiceTest {
    @Mock MarketplacePublicationPort port;
    @Mock ProductService products;
    @Mock SalesChannelService channels;
    @Mock InventoryService inventory;
    @Mock MarketplaceListingService listings;
    @Mock PublicationJournalService journal;
    @Mock StockSynchronizationService synchronization;
    MarketplacePublicationService service;
    PublicationRequestDTO request;
    Product product;
    SalesChannel channel;
    PublicationOptionsDTO options;
    final PublishedListing published = new PublishedListing("MLB987", "https://example.com/item", ListingStatus.ACTIVE);
    final MarketplaceListingResponseDTO linked = new MarketplaceListingResponseDTO(10L, 1L, 2L, "MLB987", "https://example.com/item", ListingStatus.ACTIVE, Instant.now());

    @BeforeEach void setUp() {
        service = new MarketplacePublicationService(port, products, channels, inventory, listings, journal, new ObjectMapper(), synchronization);
        request = new PublicationRequestDTO(UUID.randomUUID(), 1L, 2L, "Teclado", "MLB123", BigDecimal.TEN,
                "gold_special", List.of("https://example.com/photo.jpg"),
                List.of(new PublicationRequestDTO.Attribute("BRAND", null, "Marca")), List.of(), "me2", false, false, "Descrição");
        product = Product.builder().id(1L).active(true).build();
        channel = SalesChannel.builder().id(2L).type(ChannelType.MERCADO_LIVRE).active(true).build();
        options = new PublicationOptionsDTO("MLB123", "Teclados", true, 60, 10,
                List.of(new PublicationOptionsDTO.Field("BRAND", "Marca", true, "string", List.of(), List.of(), "")),
                List.of(), List.of(new PublicationOptionsDTO.Value("gold_special", "Clássico")), List.of("me2"), false);
    }

    void ready() {
        when(channels.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(products.getProductEntityById(1L)).thenReturn(product);
        when(inventory.getAvailableQuantity(1L)).thenReturn(7);
        when(port.getOptions(2L, "MLB123")).thenReturn(options);
    }

    void publicationSucceeds() {
        when(port.publish(request, 7)).thenReturn(published);
        when(listings.registerPublished(1L, 2L, published)).thenReturn(linked);
    }

    @Test void shouldValidatePublishThenPersistRemoteIdentityBeforeDescriptionAndLink() {
        ready(); publicationSucceeds();
        var result = service.publish(request);
        assertThat(result.listing()).isEqualTo(linked);
        assertThat(result.warnings()).isEmpty();
        InOrder order = inOrder(port, journal, listings);
        order.verify(port).getOptions(2L, "MLB123");
        order.verify(port).validate(request, 7);
        order.verify(journal).reserve(eq(request), anyString());
        order.verify(port).publish(request, 7);
        order.verify(journal).created(request.requestId(), published);
        order.verify(listings).registerPublished(1L, 2L, published);
        order.verify(port).addDescription(2L, "MLB987", "Descrição");
        order.verify(journal).linked(request.requestId(), 10L, true);
    }

    @Test void validationShouldNotPublishOrReserve() {
        ready();
        when(port.validate(request, 7)).thenReturn(List.of("shipping.me2_adoption_mandatory"));
        assertThat(service.validate(request).warnings()).containsExactly("shipping.me2_adoption_mandatory");
        verify(port).validate(request, 7);
        verify(port, never()).publish(any(), anyInt());
        verifyNoInteractions(journal, listings);
    }

    @Test void inactiveChannelShouldStopBeforeAccessingMarketplace() {
        channel.setActive(false);
        when(channels.getSalesChannelEntityById(2L)).thenReturn(channel);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(InvalidSalesChannelException.class);
        verifyNoInteractions(port);
    }

    @Test void inactiveProductShouldNotBePublished() {
        when(channels.getSalesChannelEntityById(2L)).thenReturn(channel);
        product.setActive(false);
        when(products.getProductEntityById(1L)).thenReturn(product);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationValidationException.class);
        verifyNoInteractions(port);
    }

    @Test void noStockShouldStopBeforeMarketplaceRequest() {
        when(channels.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(products.getProductEntityById(1L)).thenReturn(product);
        when(inventory.getAvailableQuantity(1L)).thenReturn(0);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationValidationException.class);
        verifyNoInteractions(port);
    }

    @Test void missingRequiredAttributeShouldBeRejectedBeforeReserve() {
        ready();
        options = new PublicationOptionsDTO("MLB123", "Teclados", true, 60, 10,
                List.of(new PublicationOptionsDTO.Field("MODEL", "Modelo", true, "string", List.of(), List.of(), "")),
                List.of(), options.listingTypes(), List.of("me2"), false);
        when(port.getOptions(2L, "MLB123")).thenReturn(options);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationValidationException.class);
        verify(journal, never()).reserve(any(), anyString());
        verify(port, never()).publish(any(), anyInt());
    }

    @Test void externalValidationFailureShouldNotReserveOrPublish() {
        ready();
        doThrow(new PublicationValidationException(List.of("item.invalid"))).when(port).validate(request, 7);
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationValidationException.class);
        verify(journal, never()).reserve(any(), anyString());
        verify(port, never()).publish(any(), anyInt());
    }

    @Test void deterministicPublishRejectionShouldMarkRejected() {
        ready();
        when(port.publish(request, 7)).thenThrow(new PublicationValidationException(List.of("item.invalid")));
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationValidationException.class);
        verify(journal).rejected(request.requestId());
        verifyNoInteractions(listings);
    }

    @Test void timeoutShouldKeepReservationToPreventDuplicateCreation() {
        ready();
        when(port.publish(request, 7)).thenThrow(new MarketplaceIntegrationException("Unknown outcome"));
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(MarketplaceIntegrationException.class);
        verify(journal).reserve(eq(request), anyString());
        verify(journal, never()).rejected(any());
        verify(journal, never()).created(any(), any());
        verifyNoInteractions(listings);
    }

    @Test void descriptionFailureShouldReturnCreatedItemWithWarning() {
        ready(); publicationSucceeds();
        doThrow(new MarketplaceIntegrationException("Description failed")).when(port).addDescription(2L, "MLB987", "Descrição");
        var response = service.publish(request);
        assertThat(response.listing()).isEqualTo(linked);
        assertThat(response.warnings()).containsExactly("description_not_saved");
        verify(journal).linked(request.requestId(), 10L, false);
        verify(port, times(1)).publish(request, 7);
    }

    @Test void sameRequestIdWithDifferentPayloadShouldBeRejected() {
        when(journal.find(request.requestId())).thenReturn(Optional.of(MarketplacePublication.builder().requestHash("another-hash").build()));
        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(PublicationConflictException.class);
        verifyNoInteractions(port);
    }

    @Test void stockChangeDuringRemoteCreationShouldBeReconciledAfterLocalLink() {
        ready(); publicationSucceeds();
        when(inventory.getAvailableQuantity(1L)).thenReturn(7, 6);
        service.publish(request);
        InOrder order = inOrder(listings, synchronization);
        order.verify(listings).registerPublished(1L, 2L, published);
        order.verify(synchronization).synchronizeProductStock(1L);
    }
}
