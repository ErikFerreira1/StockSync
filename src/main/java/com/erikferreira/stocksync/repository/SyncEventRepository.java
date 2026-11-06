package com.erikferreira.stocksync.repository;


import com.erikferreira.stocksync.entity.SyncEvent;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, Long>, JpaSpecificationExecutor<SyncEvent> {
    @Override
    @EntityGraph(attributePaths = {"product", "salesChannel", "order"})
    Page<SyncEvent> findAll(Specification<SyncEvent> specification, Pageable pageable);
    Page<SyncEvent> findByProductIdOrderByTimestampDesc(Long productId, Pageable pageable);

    Page<SyncEvent> findByStatus(SyncStatus status, Pageable pageable);
}
