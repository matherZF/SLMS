package com.wms.controller;

import com.wms.common.Result;
import com.wms.entity.EsTag;
import com.wms.repository.EsTagRepository;
import com.wms.service.EsTagService;
import com.wms.service.EslService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/es-tags")
public class EsTagController {

    private final EsTagService s;
    private final EslService eslService;
    private final EsTagRepository tagRepo;

    public EsTagController(EsTagService s, EslService eslService, EsTagRepository tagRepo) {
        this.s = s;
        this.eslService = eslService;
        this.tagRepo = tagRepo;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('page:master')")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(s.list(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('page:master')")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return s.get(id).map(Result::ok).orElseGet(() -> Result.fail("电子标签不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> r) {
        return Result.ok(s.save(null, r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> r) {
        return Result.ok(s.save(id, r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        s.delete(id);
        return Result.ok(null);
    }

    /** 绑定/解绑库位；body = { locationId: 1 } 或 { locationId: null } */
    @PostMapping("/{id}/bind-location")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Map<String, Object>> bind(@PathVariable Long id, @RequestBody Map<String, Object> r) {
        Long locationId = r.get("locationId") == null ? null : Long.valueOf(String.valueOf(r.get("locationId")));
        return Result.ok(s.bindLocation(id, locationId));
    }

    /** 手动同步运行时信息（电量、在线、固件版本），一般由定时任务/回调驱动 */
    @PostMapping("/runtime-sync")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Integer> syncRuntime(@RequestBody Map<String, Object> r) {
        String mac = (String) r.get("mac");
        Integer power = r.get("power") == null ? null : Integer.valueOf(String.valueOf(r.get("power")));
        Boolean online = r.get("online") == null ? null : Boolean.valueOf(String.valueOf(r.get("online")));
        String firmwareVersion = (String) r.get("firmwareVersion");
        return Result.ok(s.syncRuntime(mac, power, online, firmwareVersion));
    }

    /**
     * 从墨水屏服务器批量同步标签信息（电量/在线/固件）。
     * <p>
     * 1. 拉取所有已登记的 EsTag MAC
     * 2. POST 给墨水屏服务器 /wms/associate/deviceInfo
     * 3. 用返回的电量/在线/固件逐个回填 wms_es_tag
     */
    @PostMapping("/sync-from-device")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Map<String, Object>> syncFromDevice() {
        List<EsTag> all = tagRepo.findAll();
        if (all.isEmpty()) {
            Map<String, Object> r = new HashMap<>();
            r.put("total", 0);
            r.put("updated", 0);
            r.put("note", "系统中没有登记任何电子标签");
            return Result.ok(r);
        }

        List<String> macs = new ArrayList<>();
        for (EsTag t : all) macs.add(t.getMac());

        Map<String, Object> qr = eslService.queryDeviceInfo(macs);
        if (qr.get("success") != Boolean.TRUE) {
            String err = (String) qr.getOrDefault("error", "墨水屏服务器查询失败");
            return Result.fail("查询失败: " + err);
        }

        int updated = 0;
        Object itemsObj = qr.get("items");
        if (itemsObj instanceof List<?> items) {
            for (Object it : items) {
                if (!(it instanceof Map<?, ?> m)) continue;
                String mac = str(m.get("mac"));
                if (mac == null) continue;

                Integer power = toInt(m.get("power"));
                // 电量字段可能叫 power / battery / batteryLevel
                if (power == null) power = toInt(m.get("battery"));
                if (power == null) power = toInt(m.get("batteryLevel"));

                Boolean online = toBool(m.get("online"));
                if (online == null) online = toBool(m.get("isOnline"));
                // 墨水屏可能返回 int 0/1
                if (online == null) {
                    Integer oi = toInt(m.get("online"));
                    if (oi != null) online = oi == 1;
                }

                String fw = str(m.get("firmwareVersion"));
                if (fw == null) fw = str(m.get("firmware"));
                if (fw == null) fw = str(m.get("firmwareVer"));

                updated += s.syncRuntime(mac, power, online, fw);
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("total", macs.size());
        resp.put("updated", updated);
        resp.put("queried", itemsObj instanceof List<?> l ? l.size() : 0);
        return Result.ok(resp);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private static Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        String v = String.valueOf(o).trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v)) return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v)) return false;
        return null;
    }
}
