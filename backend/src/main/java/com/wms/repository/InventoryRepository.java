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

    /** 按货品+库位+批次精确查找库存记录 */
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

    /** 按货品+库位+批次加悲观锁，供移库等并发写场景使用 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId and i.location.id = :locationId and i.batch.id = :batchId")
    Optional<Inventory> findByProductIdAndLocationIdAndBatchIdForUpdate(@Param("productId") Long productId, @Param("locationId") Long locationId, @Param("batchId") Long batchId);

    /** 按货品+库位（无批次）加悲观锁，供移库等并发写场景使用 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId and i.location.id = :locationId and i.batch is null")
    Optional<Inventory> findByProductIdAndLocationIdAndBatchIsNullForUpdate(@Param("productId") Long productId, @Param("locationId") Long locationId);
}
