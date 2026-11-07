package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 电子标签信号的执行器：只在业务事务提交成功之后才真正去调墨水屏服务器。
 * <p>
 * 三个要点：
 * <ul>
 *   <li>{@code AFTER_COMMIT}：库存已经落库、行锁已经释放，HTTP 慢不再拖住数据库；</li>
 *   <li>事务回滚时不会点灯——不会再出现"单据没建成，灯却亮了、还要人手动灭"的残留；</li>
 *   <li>这里吞掉所有异常：事务已经提交，设备调不通不能反过来让用户看到一个失败提示。</li>
 * </ul>
 * 灯亮了但业务没成，最多是人多按一次按键；灯没亮业务成了，是数据不一致。两害相权取其轻。
 */
@Component
public class DeviceSignalListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceSignalListener.class);

    private final DeviceService deviceService;

    public DeviceSignalListener(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLightSignal(DeviceSignalPublisher.LightSignal signal) {
        if (signal == null || signal.locationId() == null) return;
        try {
            if (signal.on()) {
                deviceService.lightUp(signal.locationId());
            } else {
                deviceService.lightOff(signal.locationId());
            }
        } catch (RuntimeException e) {
            // 事务已提交，不能抛出；只记录，由设备巡检发现
            log.warn("[Device] 库位 {} {}失败：{}",
                    signal.locationId(), signal.on() ? "点灯" : "灭灯", e.getMessage());
        }
    }
}
