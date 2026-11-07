package com.wms.repository;

import com.wms.entity.OutboundItem;
import com.wms.entity.OutboundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboundItemRepository extends JpaRepository<OutboundItem, Long> {

    /**
     * 按库位 + 出库单状态查询出库明细，<b>最近点灯的排在最前面</b>。
     * <p>
     * 按键回调入口用：标签 MAC → Location.deviceNo → Location → locationId →
     * 找到该库位上当前处于 PICKING 的出库项，只确认排在最前面的那一条。
     * <p>
     * MySQL 的 NULL 在 DESC 排序里排在最后，所以没有 lighted_at 的历史数据不会抢占第一位。
     */
    @Query("select i from OutboundItem i join fetch i.location loc join fetch i.outboundOrder o " +
            "where loc.id = :locationId and o.status = :status " +
            "order by i.lightedAt desc, i.id desc")
    List<OutboundItem> findPickingByLocationOrderByLightedDesc(@Param("locationId") Long locationId,
                                                               @Param("status") OutboundStatus status);

    /** 亮灯结果回调入口用：cmdtoken 原样带回，反查当时发起点灯请求的出库项 */
    Optional<OutboundItem> findByLightCmdtoken(String lightCmdtoken);

    Optional<OutboundItem> findFirstById(Long id);
}
