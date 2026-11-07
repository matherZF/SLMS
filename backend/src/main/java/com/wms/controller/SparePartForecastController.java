package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.SparePartForecastVO;
import com.wms.service.SparePartForecastService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 备件需求预测。与预警中心共用查看权限——预测属于"要提前备货"的同一类信息，
 * 单独拆一个权限项只会让现场配权限时多一层麻烦。
 */
@RestController
@RequestMapping("/api/spare-part-forecasts")
public class SparePartForecastController {

    private final SparePartForecastService service;

    public SparePartForecastController(SparePartForecastService service) {
        this.service = service;
    }

    /** 全部在用备件的预测，含无法预测的（附缺口说明），供页面完整展示 */
    @GetMapping
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<List<SparePartForecastVO>> list() {
        return Result.ok(service.forecast());
    }
}
