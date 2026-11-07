package com.wms.service;

import com.wms.common.BizException;
import com.wms.entity.SystemConfig;
import com.wms.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统配置：优先读库，读不到或值非法时回落到出厂默认值。
 *
 * <p>这条回落链是刻意的——配置项被误删、表还没建好、或者有人填了个空值，
 * 系统都应该照常跑，而不是启动失败或算出个 0 天。</p>
 */
class SystemConfigTests {

    private static SystemConfigService service(SystemConfigRepository repo) {
        // 出厂默认值：寿命预警 3 / 效期 30 / 安全天数 7 / 默认交期 7 / 取样 7 / 最短样本 3
        return new SystemConfigService(repo, Mockito.mock(OperationAuditService.class), 3, 30, 7, 7, 7, 3);
    }

    private static SystemConfig row(String key, String value, String defaultValue) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setDefaultValue(defaultValue);
        c.setValueType("INT");
        c.setLabel("寿命预警提前天数");
        c.setEnabled(true);
        return c;
    }

    @Test
    void 库里有值就用库里的() {
        SystemConfigRepository repo = Mockito.mock(SystemConfigRepository.class);
        Mockito.when(repo.findByConfigKey(SystemConfigService.K_LIFE_WARNING_DAYS))
                .thenReturn(Optional.of(row(SystemConfigService.K_LIFE_WARNING_DAYS, "10", "3")));
        assertEquals(10, service(repo).lifeWarningDays(), "页面改过的 10 天应当优先生效");
    }

    @Test
    void 库里没有就回落到出厂默认值() {
        SystemConfigRepository repo = Mockito.mock(SystemConfigRepository.class);
        Mockito.when(repo.findByConfigKey(Mockito.anyString())).thenReturn(Optional.empty());
        SystemConfigService s = service(repo);
        assertEquals(3, s.lifeWarningDays());
        assertEquals(7, s.safetyDays());
        assertEquals(30, s.expiryAheadDays());
    }

    @Test
    void 值非法或已停用时也回落() {
        SystemConfigRepository repo = Mockito.mock(SystemConfigRepository.class);
        Mockito.when(repo.findByConfigKey(SystemConfigService.K_SAFETY_DAYS))
                .thenReturn(Optional.of(row(SystemConfigService.K_SAFETY_DAYS, "abc", "7")));
        assertEquals(7, service(repo).safetyDays());

        SystemConfig disabled = row(SystemConfigService.K_SAFETY_DAYS, "15", "7");
        disabled.setEnabled(false);
        Mockito.when(repo.findByConfigKey(SystemConfigService.K_SAFETY_DAYS)).thenReturn(Optional.of(disabled));
        assertEquals(7, service(repo).safetyDays(), "停用项不应生效");
    }

    @Test
    void 越界值拒绝保存() {
        SystemConfigRepository repo = Mockito.mock(SystemConfigRepository.class);
        SystemConfig c = row(SystemConfigService.K_LIFE_WARNING_DAYS, "3", "3");
        c.setId(1L);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.of(c));
        SystemConfigService s = service(repo);

        assertThrows(BizException.class, () -> s.update(1L, "0"), "0 天等于不预警，应拒绝");
        assertThrows(BizException.class, () -> s.update(1L, "500"), "500 天超出上限");
        assertThrows(BizException.class, () -> s.update(1L, "三天"), "非数字应拒绝");
    }

    @Test
    void 恢复默认回到出厂值() {
        SystemConfigRepository repo = Mockito.mock(SystemConfigRepository.class);
        SystemConfig c = row(SystemConfigService.K_SAFETY_DAYS, "21", "7");
        c.setId(2L);
        Mockito.when(repo.findById(2L)).thenReturn(Optional.of(c));
        Mockito.when(repo.save(Mockito.any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));

        service(repo).reset(2L);
        assertEquals("7", c.getConfigValue(), "恢复默认后应回到出厂值 7");
    }
}
