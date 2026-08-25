package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.product.id = :productId
            """)
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    @Query("SELECT i FROM Inventory i WHERE i.availableQuantity <= i.minQuantity")
    List<Inventory> findAllBelowMinimum();

}
