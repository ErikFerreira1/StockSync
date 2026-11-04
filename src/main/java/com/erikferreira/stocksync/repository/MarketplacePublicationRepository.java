package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.MarketplacePublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface MarketplacePublicationRepository extends JpaRepository<MarketplacePublication, UUID> {
    @Modifying
    @Query(value = "INSERT INTO marketplace_publications (request_id, request_hash, product_id, sales_channel_id, state) "
            + "VALUES (:id, :hash, :productId, :channelId, 'STARTED') ON CONFLICT (request_id) DO NOTHING", nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("hash") String hash,
                @Param("productId") Long productId, @Param("channelId") Long channelId);
}
