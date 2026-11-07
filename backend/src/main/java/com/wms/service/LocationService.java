package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.LocationDTO;
import com.wms.entity.EsTag;
import com.wms.entity.Location;
import com.wms.repository.EsTagRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LocationService {
    private final LocationRepository locationRepo;
    private final InventoryRepository inventoryRepo;
    private final OperationAuditService auditService;
    private final EsTagRepository esTagRepo;

    public LocationService(LocationRepository locationRepo,
                           InventoryRepository inventoryRepo,
                           OperationAuditService auditService,
                           EsTagRepository esTagRepo) {
        this.locationRepo = locationRepo;
        this.inventoryRepo = inventoryRepo;
        this.auditService = auditService;
        this.esTagRepo = esTagRepo;
    }

    public Page<LocationDTO> page(String code, String area, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        String c = StringUtils.hasText(code) ? code : "";
        String a = StringUtils.hasText(area) ? area : "";
        if (!c.isEmpty() || !a.isEmpty()) {
            return locationRepo.findByCodeContainingOrAreaContaining(c, a, pageable).map(this::toDTO);
        }
        return locationRepo.findAll(pageable).map(this::toDTO);
    }

    public LocationDTO getById(Long id) {
        return locationRepo.findById(id).map(this::toDTO)
                .orElseThrow(() -> new BizException("库位不存在"));
    }

    @Transactional
    public LocationDTO create(LocationDTO dto) {
        if (!StringUtils.hasText(dto.code())) throw new BizException("code不能为空");
        if (locationRepo.existsByCode(dto.code())) throw new BizException("code已存在");
        Location l = new Location(dto.code(), dto.area(), dto.type(), dto.remark());
        applyFields(l, dto);
        locationRepo.save(l);
        auditService.record("LOCATION", l.getId(), l.getCode(), "CREATE", null, null,
                "新增库位：" + l.getWarehouseName() + "/" + l.getArea() + "/" + l.getStorageMode());
        return toDTO(l);
    }

    @Transactional
    public LocationDTO update(Long id, LocationDTO dto) {
        Location l = locationRepo.findById(id).orElseThrow(() -> new BizException("库位不存在"));
        String before = l.getArea() + "/" + l.getType() + "/" + l.isEnabled();
        applyFields(l, dto);
        locationRepo.save(l);
        auditService.record("LOCATION", l.getId(), l.getCode(), "UPDATE", null, null,
                "修改前：" + before + "；修改后：" + l.getArea() + "/" + l.getType() + "/" + l.isEnabled());
        return toDTO(l);
    }

    @Transactional
    public Map<String, Integer> initializeStandardLayout() {
        int created = 0;
        int skipped = 0;
        for (int rack = 1; rack <= 4; rack++) {
            for (int level = 1; level <= 3; level++) {
                for (int position = 1; position <= 12; position++) {
                    String code = "HJ-%02d-%02d-%02d".formatted(rack, level, position);
                    if (locationRepo.existsByCode(code)) {
                        skipped++;
                        continue;
                    }
                    Location location = new Location(code, "铁质货架区", "常温", null);
                    location.setWarehouseName("备品备件间");
                    location.setStorageMode("RACK");
                    location.setRackNo(rack);
                    location.setLevelNo(level);
                    location.setPositionNo(position);
                    locationRepo.save(location);
                    created++;
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("total", 144);
        auditService.record("LOCATION_LAYOUT", 0L, "HJ-STANDARD", "INITIALIZE", null, null,
                "初始化标准货架位；新增：" + created + "；跳过：" + skipped);
        return result;
    }

    @Transactional
    public void delete(Long id) {
        Location l = locationRepo.findById(id).orElseThrow(() -> new BizException("库位不存在"));
        if (!inventoryRepo.findAllByLocationId(id).isEmpty()) {
            throw new BizException("库位[" + l.getCode() + "]存在库存记录，无法删除");
        }
        locationRepo.deleteById(id);
        auditService.record("LOCATION", id, l.getCode(), "DELETE", null, null, "删除库位：" + l.getCode());
    }

    private LocationDTO toDTO(Location l) {
        Long esTagId = null;
        String esTagName = null;
        if (StringUtils.hasText(l.getDeviceNo())) {
            EsTag tag = esTagRepo.findByMac(l.getDeviceNo()).orElse(null);
            if (tag != null) {
                esTagId = tag.getId();
                esTagName = StringUtils.hasText(tag.getName()) ? tag.getName() : tag.getMac();
            }
        }
        return new LocationDTO(l.getId(), l.getCode(), l.getArea(), l.getType(),
                l.getWarehouseName(), l.getStorageMode(), l.getRackNo(), l.getLevelNo(),
                l.getPositionNo(), l.isEnabled(), l.getDeviceNo(), l.getRemark(),
                esTagId, esTagName);
    }

    private void applyFields(Location l, LocationDTO dto) {
        String mode = StringUtils.hasText(dto.storageMode()) ? dto.storageMode().trim().toUpperCase() : "RACK";
        if (!mode.equals("RACK") && !mode.equals("FLOOR")) throw new BizException("存储方式不正确");
        if (mode.equals("RACK")) {
            validatePositive(dto.rackNo(), "排号");
            validatePositive(dto.levelNo(), "层号");
            validatePositive(dto.positionNo(), "位置号");
        }
        l.setArea(dto.area());
        l.setType(dto.type());
        l.setWarehouseName(StringUtils.hasText(dto.warehouseName()) ? dto.warehouseName().trim() : "备品备件间");
        l.setStorageMode(mode);
        l.setRackNo(mode.equals("RACK") ? dto.rackNo() : null);
        l.setLevelNo(mode.equals("RACK") ? dto.levelNo() : null);
        l.setPositionNo(mode.equals("RACK") ? dto.positionNo() : null);
        l.setEnabled(dto.enabled() == null || dto.enabled());
        l.setDeviceNo(dto.deviceNo());
        l.setRemark(dto.remark());
    }

    private void validatePositive(Integer value, String field) {
        if (value == null || value < 1) throw new BizException(field + "必须大于0");
    }
}
