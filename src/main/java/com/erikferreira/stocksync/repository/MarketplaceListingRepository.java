package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {
}
