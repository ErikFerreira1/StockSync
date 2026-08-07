package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.IntegrationCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IntegrationCredentialRepository extends JpaRepository<IntegrationCredential, Long> {
    Optional<IntegrationCredential> findBySalesChannelId(Long id);
}
