package com.wms.repository;

import com.wms.entity.SparePartUsageSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SparePartUsageSnapshotRepository extends JpaRepository<SparePartUsageSnapshot, Long> {
    List<SparePartUsageSnapshot> findTop100ByUsageIdOrderByCollectedAtDesc(Long usageId);
}
