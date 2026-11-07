package com.wms.service;

import com.wms.common.BizException;
import com.wms.entity.EsTag;
import com.wms.entity.Location;
import com.wms.repository.EsTagRepository;
import com.wms.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class EsTagService {

    private final EsTagRepository tagRepo;
    private final LocationRepository locationRepo;
    private final OperationAuditService auditService;

    public EsTagService(EsTagRepository tagRepo, LocationRepository locationRepo, OperationAuditService auditService) {
        this.tagRepo = tagRepo;
        this.locationRepo = locationRepo;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        List<EsTag> tags = k.isEmpty()
                ? tagRepo.findAll()
                : tagRepo.findByMacContainingOrNameContaining(k, k);
        return tags.stream().map(this::tagMap).toList();
    }

    public Optional<Map<String, Object>> get(Long id) {
        return tagRepo.findById(id).map(this::tagMap);
    }

    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> req) {
        boolean creating = id == null;
        EsTag tag = creating ? new EsTag() : tagRepo.findById(id)
                .orElseThrow(() -> new BizException("电子标签不存在"));
        String oldStatus = tag.getStatus();

        String mac = required(req, "mac", "标签 MAC 不能为空").trim();
        if (creating && tagRepo.existsByMac(mac)) throw new BizException("标签 MAC 已存在：" + mac);
        tag.setMac(mac);
        tag.setName(str(req, "name"));
        tag.setType(Optional.ofNullable(str(req, "type")).orElse("LIGHT_LED"));
        tag.setFirmwareVersion(str(req, "firmwareVersion"));
        tag.setPower(integer(req, "power"));
        tag.setOnline(Boolean.TRUE.equals(req.get("online")));
        tag.setRemark(str(req, "remark"));
        tag.setStatus(Optional.ofNullable(str(req, "status")).orElse("ACTIVE"));

        Long locationId = num(req, "locationId");
        if (locationId != null) {
            Location loc = locationRepo.findById(locationId).orElseThrow(() -> new BizException("库位不存在"));
            tag.setLocationId(loc.getId());
            // 同时把 Location.deviceNo 也对齐（方便业务链路按库位找标签）
            if (!mac.equals(loc.getDeviceNo())) {
                loc.setDeviceNo(mac);
                locationRepo.save(loc);
            }
        } else if (tag.getLocationId() != null) {
            // 解除绑定时清空 Location.deviceNo
            locationRepo.findById(tag.getLocationId()).ifPresent(loc -> {
                if (mac.equals(loc.getDeviceNo())) loc.setDeviceNo(null);
                locationRepo.save(loc);
            });
            tag.setLocationId(null);
        }

        tagRepo.save(tag);
        auditService.record("ES_TAG", tag.getId(), tag.getMac(),
                creating ? "CREATE" : "UPDATE",
                oldStatus, tag.getStatus(),
                (creating ? "新增标签" : "修改标签") + " " + tag.getMac());
        return tagMap(tag);
    }

    @Transactional
    public Map<String, Object> bindLocation(Long tagId, Long locationId) {
        EsTag tag = tagRepo.findById(tagId).orElseThrow(() -> new BizException("电子标签不存在"));
        Optional<Location> oldLoc = tag.getLocationId() != null ? locationRepo.findById(tag.getLocationId()) : Optional.empty();

        if (locationId == null) {
            // 解除绑定
            oldLoc.ifPresent(loc -> {
                if (tag.getMac().equals(loc.getDeviceNo())) { loc.setDeviceNo(null); locationRepo.save(loc); }
            });
            tag.setLocationId(null);
            auditService.record("ES_TAG", tagId, tag.getMac(), "UNBIND", null, null, "解除库位绑定");
        } else {
            Location loc = locationRepo.findById(locationId).orElseThrow(() -> new BizException("库位不存在"));
            oldLoc.ifPresent(l -> { if (tag.getMac().equals(l.getDeviceNo())) { l.setDeviceNo(null); locationRepo.save(l); } });
            tag.setLocationId(loc.getId());
            loc.setDeviceNo(tag.getMac());
            locationRepo.save(loc);
            auditService.record("ES_TAG", tagId, tag.getMac(), "BIND", null, null, "绑定库位 " + loc.getCode());
        }
        tagRepo.save(tag);
        return tagMap(tag);
    }

    @Transactional
    public void delete(Long id) {
        EsTag tag = tagRepo.findById(id).orElseThrow(() -> new BizException("电子标签不存在"));
        Optional.ofNullable(tag.getLocationId()).ifPresent(locId ->
                locationRepo.findById(locId).ifPresent(loc -> {
                    if (tag.getMac().equals(loc.getDeviceNo())) { loc.setDeviceNo(null); locationRepo.save(loc); }
                }));
        tagRepo.delete(tag);
        auditService.record("ES_TAG", id, tag.getMac(), "DELETE", tag.getStatus(), null, "删除标签 " + tag.getMac());
    }

    /**
     * 从外部同步运行时信息（电量、在线、固件版本），供手动触发 / 定时任务调用。
     * 仅更新非核心字段，不覆盖 MAC / locationId 等主数据。
     */
    @Transactional
    public int syncRuntime(String mac, Integer power, Boolean online, String firmwareVersion) {
        return tagRepo.findByMac(mac).map(tag -> {
            if (power != null) tag.setPower(power);
            if (online != null) tag.setOnline(online);
            if (firmwareVersion != null) tag.setFirmwareVersion(firmwareVersion);
            if (online != null && online) tag.setLastSeenAt(Instant.now());
            tagRepo.save(tag);
            return 1;
        }).orElse(0);
    }

    // ---- map helpers ----
    private Map<String, Object> tagMap(EsTag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("mac", t.getMac());
        m.put("name", t.getName());
        m.put("type", t.getType());
        m.put("firmwareVersion", t.getFirmwareVersion());
        m.put("power", t.getPower());
        m.put("online", t.isOnline());
        m.put("lastSeenAt", t.getLastSeenAt());
        m.put("locationId", t.getLocationId());
        m.put("locationCode", t.getLocationId() != null
                ? locationRepo.findById(t.getLocationId()).map(Location::getCode).orElse(null)
                : null);
        m.put("remark", t.getRemark());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v).trim();
    }
    private String required(Map<String, Object> m, String k, String msg) {
        String v = str(m, k);
        if (v == null || v.isEmpty()) throw new BizException(msg);
        return v;
    }
    private Long num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }
    private Integer integer(Map<String, Object> m, String k) {
        Long v = num(m, k);
        return v == null ? null : v.intValue();
    }
}
