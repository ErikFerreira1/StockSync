package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoMarketplaceAdapterTest {

    private DemoMarketplaceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DemoMarketplaceAdapter();
        adapter.registerListing(
                "DEMO-MLB-1",
                10L,
                "Demo product",
                new BigDecimal("25.00"),
                12,
                ListingStatus.ACTIVE);
    }

    @Test
    void stockAndStatusUpdatesShouldBeVisibleInDemoMarketplace() {
        adapter.updateStock("DEMO-MLB-1", 9);
        adapter.updateListingStatus("DEMO-MLB-1", ListingStatus.PAUSED, null);

        var listing = adapter.findListingById("DEMO-MLB-1");

        assertThat(listing.availableQuantity()).isEqualTo(9);
        assertThat(listing.status()).isEqualTo(ListingStatus.PAUSED);
        assertThat(adapter.getCurrentStock("DEMO-MLB-1")).isEqualTo(9);
    }

    @Test
    void activationShouldUseCurrentLocalQuantity() {
        adapter.updateListingStatus("DEMO-MLB-1", ListingStatus.ACTIVE, 7);

        var listing = adapter.findListingById("DEMO-MLB-1");

        assertThat(listing.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.availableQuantity()).isEqualTo(7);
    }

    @Test
    void missingListingShouldFailClearly() {
        assertThatThrownBy(() -> adapter.getCurrentStock("missing"))
                .isInstanceOf(MarketplaceListingNotFoundException.class)
                .hasMessageContaining("missing");
    }
}
