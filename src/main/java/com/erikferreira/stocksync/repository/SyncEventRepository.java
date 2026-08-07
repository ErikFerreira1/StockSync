package com.erikferreira.stocksync.repository;


import com.erikferreira.stocksync.entity.SyncEvent;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {
    Page<SyncEvent> findByProductIdOrderByTimestampDesc(Long productId, Pageable pageable);

    Page<SyncEvent> findByStatus(SyncStatus status, Pageable pageable);
}
