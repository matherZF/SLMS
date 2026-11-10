package com.wms.service;

import com.wms.entity.EquipmentExtRef;
import com.wms.repository.EquipmentExtRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 设备取数规则解析器：把"SP 设备编码"解析成 {@link MappingRule} 列表。
 *
 * <p><b>只认配置，没有默认</b>：设备档案编码本身不再自动参与取数。
 * 一台设备要拿到运行数据，必须在设备编辑页配置映射行（源 + 外部编码 + 指标），
 * 配了几行就按几行取；没配 = 不取任何数据。这样页面上配的就是实际取的口径，
 * 不会出现"明明没配却出现数据"或"某个源悄悄供了两份"的情况。</p>
 *
 * <p>同一指标被多行命中（例如两个源都勾了件数）时由调用方合计——
 * 需要"件数只认 WCS"就把另一个源的件数勾掉即可。</p>
 */
@Service
public class DeviceCodeResolver {

    private final EquipmentExtRefRepository refRepo;

    public DeviceCodeResolver(EquipmentExtRefRepository refRepo) {
        this.refRepo = refRepo;
    }

    /**
     * 一条取数规则：从某个外部系统的某个编码取哪些指标。
     *
     * @param sourceSys 外部系统（WCS / WMS / DT），对应 wms_device_stat_daily.source_sys
     * @param extCode   外部系统里的设备编码
     */
    public record MappingRule(String sourceSys, String extCode, boolean takeHours, boolean takeTasks, boolean takeQty) {
        /** 该规则是否至少取一个指标；全 false 的行没有意义，保存时会被拦下 */
        public boolean takesAnything() { return takeHours || takeTasks || takeQty; }

        /** 统一大小写后的匹配键：源+编码 */
        public String matchKey() { return normalizeKey(sourceSys, extCode); }
    }

    /** 预测反向聚合用：某个（源+编码）归属哪台设备、按什么指标 */
    public record RuleRef(String equipmentCode, MappingRule rule) { }

    public static String normalizeKey(String sourceSys, String extCode) {
        return (sourceSys == null ? "" : sourceSys.trim().toUpperCase(Locale.ROOT))
                + "|" + (extCode == null ? "" : extCode.trim().toLowerCase(Locale.ROOT));
    }

    /** 设备的取数规则；没配映射返回空列表（= 该设备不取数） */
    public List<MappingRule> rules(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return List.of();
        List<MappingRule> out = new ArrayList<>();
        for (EquipmentExtRef r : refRepo.findByEquipment_CodeIgnoreCase(equipmentCode.trim())) {
            if (!StringUtils.hasText(r.getExtCode())) continue;
            out.add(new MappingRule(r.getSourceSys(), r.getExtCode().trim(),
                    r.isTakeHours(), r.isTakeTasks(), r.isTakeQty()));
        }
        return out;
    }

    /**
     * 全量规则索引：{@code 源|编码小写 → (设备编码, 规则)}，供需求预测按设备归组。
     *
     * <p>唯一键 (source_sys, ext_code) 保证一个（源+编码）只属于一台设备，不会互相覆盖。</p>
     */
    public Map<String, RuleRef> ruleIndex() {
        Map<String, RuleRef> out = new HashMap<>();
        for (EquipmentExtRef r : refRepo.findAll()) {
            if (!StringUtils.hasText(r.getExtCode()) || r.getEquipment() == null
                    || !StringUtils.hasText(r.getEquipment().getCode())) continue;
            MappingRule rule = new MappingRule(r.getSourceSys(), r.getExtCode().trim(),
                    r.isTakeHours(), r.isTakeTasks(), r.isTakeQty());
            out.put(rule.matchKey(), new RuleRef(r.getEquipment().getCode(), rule));
        }
        return out;
    }

    /** 规则涉及的外部编码（去重，保持顺序），查询时按它取行 */
    public List<String> codes(List<MappingRule> rules) {
        List<String> out = new ArrayList<>();
        for (MappingRule r : rules) {
            if (r.extCode() != null && !out.contains(r.extCode())) out.add(r.extCode());
        }
        return out;
    }

    /** 规则涉及的来源系统（去重，大写） */
    public List<String> sources(List<MappingRule> rules) {
        List<String> out = new ArrayList<>();
        for (MappingRule r : rules) {
            String s = r.sourceSys() == null ? "" : r.sourceSys().trim().toUpperCase(Locale.ROOT);
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }
}
