package com.wms.repository;

import com.wms.entity.DataDictionary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DataDictionaryRepository extends JpaRepository<DataDictionary, Long> {
    boolean existsByDictTypeAndItemCode(String dictType, String itemCode);
    List<DataDictionary> findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(String dictType);
    Page<DataDictionary> findByDictType(String dictType, Pageable pageable);
}
