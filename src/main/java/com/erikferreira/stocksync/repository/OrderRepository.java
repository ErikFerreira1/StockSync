package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.SalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findBySalesChannelAndExternalOrderId(SalesChannel salesChannel, String externalOrderId);
}
