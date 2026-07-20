package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.SalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesChannelRepository extends JpaRepository<SalesChannel, Long> {
}
