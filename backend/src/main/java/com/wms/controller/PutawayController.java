package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.PutawayConfirmReq;
import com.wms.service.PutawayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/putaway")
public class PutawayController {
    private final PutawayService putawayService;

    public PutawayController(PutawayService putawayService) {
        this.putawayService = putawayService;
    }

    @PreAuthorize("hasAuthority('inbound:putaway')")
    @PostMapping("/confirm")
    public Result<Map<String, Object>> confirm(@RequestBody PutawayConfirmReq req) {
        return Result.ok(putawayService.confirm(req));
    }
}
