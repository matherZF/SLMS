package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.DeviceRuntimeViewVO;
import com.wms.service.DeviceRuntimeViewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipment-runtime")
@ConditionalOnProperty(prefix = "device.datasource", name = "enabled", havingValue = "true")
public class DeviceRuntimeViewController {
    private final DeviceRuntimeViewService service;

    public DeviceRuntimeViewController(DeviceRuntimeViewService service) {
        this.service = service;
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) String equipmentNo) {
        if (equipmentNo == null || equipmentNo.isBlank()) {
            List<DeviceRuntimeViewVO> rows = service.findAll();
            return Result.ok(rows);
        }
        return Result.ok(service.findByEquipmentNo(equipmentNo.trim()));
    }
}
