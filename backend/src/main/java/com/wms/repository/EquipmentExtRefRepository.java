package com.wms.repository;

import com.wms.entity.EquipmentExtRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentExtRefRepository extends JpaRepository<EquipmentExtRef, Long> {

    List<EquipmentExtRef> findByEquipmentId(Long equipmentId);

    /** 按设备档案编码找映射（编码忽略大小写，与运行数据匹配口径一致） */
    List<EquipmentExtRef> findByEquipment_CodeIgnoreCase(String equipmentCode);

    Optional<EquipmentExtRef> findBySourceSysIgnoreCaseAndExtCodeIgnoreCase(String sourceSys, String extCode);

    boolean existsBySourceSysIgnoreCaseAndExtCodeIgnoreCase(String sourceSys, String extCode);

    /**
     * 批量删除某设备的全部映射。
     *
     * <p>必须用 JPQL 批量删除而不是派生的 deleteByEquipmentId：派生版是"查出实体再
     * em.remove"，删除动作进队列，事务提交时 Hibernate 先执行 INSERT 再执行 DELETE，
     * "先删后插"的同编码映射会先撞唯一键 (source_sys, ext_code)。批量删除立即下发
     * SQL DELETE，删完再插才安全。调用方需在事务内（设备保存本身是 @Transactional）。</p>
     */
    @Modifying
    @Query("delete from EquipmentExtRef r where r.equipment.id = :equipmentId")
    void deleteByEquipmentIdBulk(@Param("equipmentId") Long equipmentId);
}
