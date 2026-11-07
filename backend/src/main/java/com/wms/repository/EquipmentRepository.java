package com.wms.repository;
import com.wms.entity.Equipment; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface EquipmentRepository extends JpaRepository<Equipment,Long>{ boolean existsByCode(String code); List<Equipment> findByCodeContainingOrNameContaining(String code,String name); }
