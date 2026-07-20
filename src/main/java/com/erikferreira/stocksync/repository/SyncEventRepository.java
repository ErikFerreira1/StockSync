package com.erikferreira.stocksync.repository;


import com.erikferreira.stocksync.entity.SyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {
}
