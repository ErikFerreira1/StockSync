package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {

    Optional<MarketplaceListing> findByListingIdAndSalesChannelType(String listingId, ChannelType salesChannelType);

}
