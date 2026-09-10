package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {

    Optional<MarketplaceListing> findByListingIdAndSalesChannelType(String listingId, ChannelType salesChannelType);

    Optional<MarketplaceListing> findByListingIdAndSalesChannelId(String listingId, Long salesChannelId);

    boolean existsBySalesChannelIdAndListingId(Long salesChannelId, String listingId);

    List<MarketplaceListing> findByProductIdAndStatusAndSalesChannelActiveTrue(
            Long productId,
            ListingStatus status);

    List<MarketplaceListing> findByProductIdAndStatusAndPausedByProductDeactivationTrueAndSalesChannelActiveTrue(
            Long productId,
            ListingStatus status);

}
