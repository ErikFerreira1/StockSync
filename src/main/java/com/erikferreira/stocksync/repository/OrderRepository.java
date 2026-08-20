package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"salesChannel"})
    @Override
    Page<Order> findAll(Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o IN :orders")
    List<Order> fetchItemsForOrders(List<Order> orders);

    boolean existsBySalesChannelIdAndExternalOrderId(Long salesChannelId, String externalOrderId);
}
