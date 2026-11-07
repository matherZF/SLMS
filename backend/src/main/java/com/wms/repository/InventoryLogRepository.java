package com.wms.repository;

import com.wms.entity.InventoryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    List<InventoryLog> findTop10ByOrderByCreatedAtDesc();
    List<InventoryLog> findByRefNoOrderByIdAsc(String refNo);

    @Query("select l from InventoryLog l where " +
            "(:sku is null or l.sku like %:sku% or l.productName like %:sku%) and " +
            "(:locationCode is null or l.locationCode like %:locationCode%) and " +
            "(:changeType is null or l.changeType = :changeType) " +
            "order by l.createdAt desc")
    Page<InventoryLog> search(@Param("sku") String sku,
                              @Param("locationCode") String locationCode,
                              @Param("changeType") String changeType,
                              Pageable pageable);
}
