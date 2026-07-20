package com.erikferreira.stocksync.repository;

import com.erikferreira.stocksync.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "inventory")
    @Override
    Page<Product> findAll(Pageable pageable);
}
