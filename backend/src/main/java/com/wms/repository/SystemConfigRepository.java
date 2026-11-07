package com.wms.repository;

import com.wms.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String configKey);

    @Query("select c from SystemConfig c where c.enabled = true order by c.sortOrder, c.id")
    List<SystemConfig> findAllEnabledOrdered();
}
