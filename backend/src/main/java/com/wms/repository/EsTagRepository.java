package com.wms.repository;

import com.wms.entity.EsTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EsTagRepository extends JpaRepository<EsTag, Long> {

    Optional<EsTag> findByMac(String mac);

    boolean existsByMac(String mac);

    List<EsTag> findByMacContainingOrNameContaining(String mac, String name);

    List<EsTag> findByLocationId(Long locationId);

    List<EsTag> findByOnline(boolean online);
}
