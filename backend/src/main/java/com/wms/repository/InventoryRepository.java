package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductIdAndLocationId(Long productId, Long locationId);

    /** 按商品+库位+批次精确查找库存记录 */
    Optional<Inventory> findByProductIdAndLocationIdAndBatchId(Long productId, Long locationId, Long batchId);

    List<Inventory> findAllByProductId(Long productId);
    List<Inventory> findAllByLocationId(Long locationId);

    @EntityGraph(attributePaths = {"product", "location", "batch"})
    @Query("select i from Inventory i")
    List<Inventory> findAllWithGraph();

    @EntityGraph(attributePaths = {"product", "location", "batch"})
    @Query("select i from Inventory i where i.id = :id")
    Optional<Inventory> findWithGraphById(@Param("id") Long id);

    Optional<Inventory> findByProductIdAndLocationIdAndBatchIsNull(Long productId, Long locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"product", "location", "batch"})
    @Query("select i from Inventory i where i.id = :id")
    Optional<Inventory> findWithGraphForUpdate(@Param("id") Long id);
}
