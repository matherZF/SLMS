package com.wms.repository;

import com.wms.entity.PutawayItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PutawayItemRepository extends JpaRepository<PutawayItem, Long> {

    /** 上架确认幂等键反查：同一个请求重复提交时用来识别重放，不再重复入库 */
    Optional<PutawayItem> findByRequestId(String requestId);
}
