package com.wms.repository;

import com.wms.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    Optional<Batch> findByItemKey(String itemKey);
    List<Batch> findByProductId(Long productId);
    boolean existsByItemKey(String itemKey);
}
