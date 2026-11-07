package com.wms.repository;
import com.wms.entity.EquipmentPart; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface EquipmentPartRepository extends JpaRepository<EquipmentPart,Long>{
 @EntityGraph(attributePaths={"equipment","product"}) List<EquipmentPart> findByEquipmentId(Long equipmentId);
 /** 设备编辑弹框里的关联备件分页列表 */
 @EntityGraph(attributePaths={"equipment","product"}) org.springframework.data.domain.Page<EquipmentPart> findByEquipmentId(Long equipmentId, org.springframework.data.domain.Pageable pageable);
 @EntityGraph(attributePaths={"equipment","product"}) List<EquipmentPart> findAll();
 /**
  * 按备件反查装机设备。出库建台账时用它确定「这个备件该按哪台设备的累计量算寿命」。
  * 同一备件可能装在多台设备上（唯一键是 equipment_id + product_id），所以返回列表。
  */
 @EntityGraph(attributePaths={"equipment","product"}) List<EquipmentPart> findByProductId(Long productId);
 Optional<EquipmentPart> findByEquipmentIdAndProductId(Long equipmentId,Long productId);
}
