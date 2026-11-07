package com.wms.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 电子标签亮灯/灭灯信号的发布器。
 * <p>
 * 出库流程原来在自己的事务里直接调墨水屏服务器的 HTTP 接口（超时 3s/5s），
 * 而那时正持有库存行锁。墨屏服务器一慢，一个 10 项的单据会把库存行锁住近半分钟；
 * 连接池只有 10 个连接，几个并发就能把全站拖到 {@code Connection is not available}。
 * <p>
 * 现在业务代码只负责"记下要亮哪个库位"，真正的 HTTP 调用交给
 * {@link DeviceSignalListener} 在事务提交后执行，锁已经释放，设备故障也不再影响库存事务。
 */
@Component
public class DeviceSignalPublisher {

    /** 一次点灯/灭灯意图。on=true 亮灯，false 灭灯 */
    public record LightSignal(Long locationId, boolean on) {}

    private final ApplicationEventPublisher events;

    public DeviceSignalPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** 事务提交后点亮该库位的电子标签 */
    public void light(Long locationId) {
        if (locationId != null) events.publishEvent(new LightSignal(locationId, true));
    }

    /** 事务提交后熄灭该库位的电子标签 */
    public void lightOff(Long locationId) {
        if (locationId != null) events.publishEvent(new LightSignal(locationId, false));
    }
}
