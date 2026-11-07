package com.wms.repository;
import com.wms.entity.SparePartUsage; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.Optional;
public interface SparePartUsageRepository extends JpaRepository<SparePartUsage,Long>{
 boolean existsByOutboundItemId(Long outboundItemId);
 @Query("select u from SparePartUsage u where (:status is null or u.status=:status) and (:keyword is null or lower(u.usageNo) like lower(concat('%',:keyword,'%')) or lower(u.outboundOrderNo) like lower(concat('%',:keyword,'%')) or lower(u.sku) like lower(concat('%',:keyword,'%')) or lower(u.productName) like lower(concat('%',:keyword,'%')) or lower(coalesce(u.equipmentCode,'')) like lower(concat('%',:keyword,'%'))) ")
 Page<SparePartUsage> search(@Param("status")String status,@Param("keyword")String keyword,Pageable pageable);
 Optional<SparePartUsage> findByOutboundItemId(Long outboundItemId);
 @Query("select u from SparePartUsage u where u.lifeMetric <> 'NONE' and u.activeQty > 0 and u.status not in ('RETURNED','CLOSED') order by u.id")
 java.util.List<SparePartUsage> findTrackable();
 java.util.List<SparePartUsage> findByStatusInOrderByIdDesc(java.util.Collection<String> statuses);
 @Modifying
 @Query("update SparePartUsage u set u.activeQty=u.qty where u.activeQty=0 and u.returnedQty=0 and u.endedAt is null")
 int initializeActiveQtyForExistingData();
}
