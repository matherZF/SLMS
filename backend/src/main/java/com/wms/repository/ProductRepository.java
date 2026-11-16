package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findBySkuContainingOrNameContaining(String sku, String name, Pageable pageable);
    boolean existsBySku(String sku);
    List<Product> findBySkuContainingOrNameContaining(String sku, String name);

    /** 取某编码前缀下所有 sku，用于流水号自动生成时求最大值 */
    @Query("select p.sku from Product p where p.sku like concat(?1, '%')")
    List<String> findSkusStartingWith(String prefix);
}
