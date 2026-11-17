package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);

    /** SKU/名称组合查询：非空条件 AND 组合，空条件不过滤。
     *  原 OR 派生查询在只填一个条件时，另一个空串 like '%%' 命中全部，导致查询失效。 */
    @Query("select p from Product p where (:sku = '' or p.sku like concat('%', :sku, '%')) and (:name = '' or p.name like concat('%', :name, '%'))")
    Page<Product> searchBySkuAndName(String sku, String name, Pageable pageable);

    /** 取某编码前缀下所有 sku，用于流水号自动生成时求最大值 */
    @Query("select p.sku from Product p where p.sku like concat(?1, '%')")
    List<String> findSkusStartingWith(String prefix);
}
