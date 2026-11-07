package com.wms.repository;

import com.wms.entity.StocktakeOrder;
import com.wms.entity.StocktakeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StocktakeOrderRepository extends JpaRepository<StocktakeOrder, Long> {
    long countByStatus(StocktakeStatus status);
    Page<StocktakeOrder> findAllByOrderByIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.inventory", "items.inventory.product", "items.inventory.location", "items.inventory.batch"})
    @Query("select o from StocktakeOrder o where o.id=:id")
    Optional<StocktakeOrder> findDetail(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "items.inventory", "items.inventory.product", "items.inventory.location", "items.inventory.batch"})
    @Query("select o from StocktakeOrder o where o.id=:id")
    Optional<StocktakeOrder> findDetailForUpdate(@Param("id") Long id);
}
