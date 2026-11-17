package com.wms.repository;

import com.wms.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    /** 编码/区域组合查询：非空条件 AND 组合，空条件不过滤（原 OR 派生查询空串 like '%%' 会命中全部） */
    @Query("select l from Location l where (:code = '' or l.code like concat('%', :code, '%')) and (:area = '' or l.area like concat('%', :area, '%'))")
    Page<Location> searchByCodeAndArea(String code, String area, Pageable pageable);
    boolean existsByCode(String code);
    List<Location> findByCodeContaining(String code);

    /** 按电子标签 MAC 查询绑定的库位（Location.deviceNo 存 MAC） */
    Location findByDeviceNo(String deviceNo);
}
