package com.erikferreira.stocksync.entity;

import com.erikferreira.stocksync.entity.enums.ListingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "marketplace_listings", uniqueConstraints = @UniqueConstraint(name = "uk_marketplace_listings_channel_listing", columnNames = {"sales_channel_id", "listing_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    @Column(name = "listing_id", nullable = false)
    private String listingId;

    @Column(name = "listing_url", length = 1000)
    private String listingUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ListingStatus status;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
