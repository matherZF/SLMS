package com.wms.repository;

import com.wms.entity.InterfaceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterfaceLogRepository extends JpaRepository<InterfaceLog, Long>, JpaSpecificationExecutor<InterfaceLog> {
}
