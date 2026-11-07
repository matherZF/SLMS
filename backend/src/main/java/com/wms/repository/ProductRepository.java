package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findBySkuContainingOrNameContaining(String sku, String name, Pageable pageable);
    boolean existsBySku(String sku);
    List<Product> findBySkuContainingOrNameContaining(String sku, String name);
}
