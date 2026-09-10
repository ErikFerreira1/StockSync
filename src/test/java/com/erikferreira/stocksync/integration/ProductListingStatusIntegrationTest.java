package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.MarketplaceListingService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductListingStatusIntegrationTest extends PostgreSQLIntegrationTest {

    @MockitoBean
    private MarketplaceIntegrationPort marketplaceIntegrationPort;

    @Autowired
    private ProductService productService;

    @Autowired
    private SalesChannelService salesChannelService;

    @Autowired
    private MarketplaceListingService listingService;

    @Autowired
    private MarketplaceListingRepository listingRepository;

    @Test
    void productDeactivationShouldPauseAndReactivationShouldRestoreOnlyManagedListing() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO(
                "SKU-STATUS-" + suffix,
                "Status product",
                null,
                BigDecimal.TEN,
                10,
                2));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO(
                "ML-STATUS-" + suffix,
                ChannelType.MERCADO_LIVRE,
                "https://api.mercadolibre.com"));
        String listingId = "MLB-STATUS-" + suffix;
        listingService.insert(new MarketplaceListingInsertDTO(
                product.id(),
                channel.id(),
                listingId,
                null,
                ListingStatus.ACTIVE));

        productService.deactivate(product.id());

        var pausedListing = listingRepository
                .findByListingIdAndSalesChannelId(listingId, channel.id())
                .orElseThrow();
        assertThat(productService.findById(product.id()).active()).isFalse();
        assertThat(pausedListing.getStatus()).isEqualTo(ListingStatus.PAUSED);
        assertThat(pausedListing.isPausedByProductDeactivation()).isTrue();
        verify(marketplaceIntegrationPort)
                .updateListingStatus(listingId, ListingStatus.PAUSED, null);

        productService.activate(product.id());

        var activeListing = listingRepository
                .findByListingIdAndSalesChannelId(listingId, channel.id())
                .orElseThrow();
        assertThat(productService.findById(product.id()).active()).isTrue();
        assertThat(activeListing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(activeListing.isPausedByProductDeactivation()).isFalse();
        verify(marketplaceIntegrationPort)
                .updateListingStatus(listingId, ListingStatus.ACTIVE, 10);
    }
}
