package com.wms.service;

import com.wms.config.EslProperties;
import com.wms.dto.esl.EslLedColor;
import com.wms.dto.esl.EslLedMode;
import com.wms.dto.esl.EslLightLedItemDTO;
import com.wms.entity.Location;
import com.wms.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 灯光提示设备服务。
 * <p>
 * 桥接层：业务代码（出库、入库等）按库位 ID 调用本服务，
 * 本服务查询 Location.deviceNo（存储电子标签 MAC 地址，如 99.26.17.85），
 * 然后转发到 {@link EslService} 调用墨水屏服务器的点灯接口。
 * <p>
 * 当设备尚未启用时，可通过配置 {@code eslabel.enabled=false} 关闭实际 HTTP 调用，
 * 此时退化为桩实现，只输出 INFO 日志，业务主流程不受影响。
 */
@Service
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    /** 是否启用实际的电子标签调用（默认 true） */
    private final boolean eslEnabled;

    private final LocationRepository locationRepo;
    private final EslService eslService;
    private final EslProperties eslProperties;

    public DeviceService(LocationRepository locationRepo,
                         EslService eslService,
                         EslProperties eslProperties,
                         @Value("${eslabel.enabled:true}") boolean eslEnabled) {
        this.locationRepo = locationRepo;
        this.eslService = eslService;
        this.eslProperties = eslProperties;
        this.eslEnabled = eslEnabled;
    }

    /**
     * 点亮指定库位绑定的电子标签 LED（常亮 + 红色，30 秒自动熄灭）。
     * <p>
     * 库位未绑定标签 MAC → warn 日志 + 返回 null，业务主流程继续。
     * HTTP 调用超时或失败 → warn 日志 + EslService 会自动写入接口日志，业务主流程继续。
     *
     * @param locationId 库位 ID
     * @return 设备编号（标签 MAC），或 null 表示未绑定 / 调用被跳过
     */
    public String lightUp(Long locationId) {
        return lightUp(locationId, null);
    }

    /**
     * 同上，但携带 cmdtoken（用于亮灯结果回调的请求-响应匹配）。
     *
     * @param locationId 库位 ID
     * @param cmdtoken   业务方生成的 UUID，将写入 EslLightLedItemDTO.cmdtoken，回调会原样带回
     */
    public String lightUp(Long locationId, String cmdtoken) {
        Location loc = locationRepo.findById(locationId).orElse(null);
        if (loc == null) {
            log.warn("[Device] 库位不存在: {}", locationId);
            return null;
        }
        String mac = loc.getDeviceNo();
        if (mac == null || mac.isBlank()) {
            log.warn("[Device] 库位 {} 未绑定电子标签 MAC（deviceNo 为空），跳过点亮", loc.getCode());
            return null;
        }
        if (!eslEnabled) {
            log.info("[Device] ESL 调用已禁用（eslabel.enabled=false），跳过点亮库位 {} 标签 {}", loc.getCode(), mac);
            return mac;
        }

        EslLightLedItemDTO item = new EslLightLedItemDTO(mac, 30, EslLedColor.RED, EslLedMode.ON);
        item.setCmdtoken(cmdtoken);
        boolean ok = eslService.lightTagsLed(List.of(item));
        if (!ok) {
            log.warn("[Device] 点亮库位 {} 标签 {} 失败（墨水屏服务器未响应或返回非 true）。" +
                    " 请检查接口日志（ESL_LIGHT_TAGS_LED），服务器地址: {}",
                    loc.getCode(), mac, eslProperties.getServerUrl());
        } else {
            log.info("[Device] 点亮库位 {} 标签 {} 成功，cmdtoken={}", loc.getCode(), mac, cmdtoken);
        }
        return mac;
    }

    /**
     * 熄灭指定库位绑定的电子标签 LED（ledmode=3 灭灯）。
     */
    public void lightOff(Long locationId) {
        Location loc = locationRepo.findById(locationId).orElse(null);
        if (loc == null) return;
        String mac = loc.getDeviceNo();
        if (mac == null || mac.isBlank()) return;
        if (!eslEnabled) {
            log.info("[Device] ESL 调用已禁用，跳过熄灭库位 {} 标签 {}", loc.getCode(), mac);
            return;
        }

        EslLightLedItemDTO item = new EslLightLedItemDTO(mac, 0, EslLedColor.OFF, EslLedMode.OFF);
        boolean ok = eslService.lightTagsLed(List.of(item));
        if (!ok) {
            log.warn("[Device] 熄灭库位 {} 标签 {} 失败，请检查接口日志（ESL_LIGHT_TAGS_LED）", loc.getCode(), mac);
        } else {
            log.info("[Device] 熄灭库位 {} 标签 {} 成功", loc.getCode(), mac);
        }
    }
}
