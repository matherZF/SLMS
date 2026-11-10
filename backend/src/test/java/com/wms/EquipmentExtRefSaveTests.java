package com.wms;

import com.wms.repository.EquipmentExtRefRepository;
import com.wms.service.EquipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设备"外部数据编码"映射保存回归。
 *
 * <p>场景：编辑设备其他字段时，前端把已有映射原样带回，后端按"先删后插"整表替换。
 * 派生的 deleteBy 是查出实体再 remove，删除动作进队列延迟执行，Hibernate 在提交时
 * 先执行 INSERT 再执行 DELETE——同编码映射会先撞唯一键 (source_sys, ext_code)，
 * 表现为"只改个名字保存就报映射重复"。已改为 JPQL 批量删除（立即下发 DELETE）。</p>
 */
@SpringBootTest
@Transactional
class EquipmentExtRefSaveTests {

    @Autowired EquipmentService equipmentService;
    @Autowired EquipmentExtRefRepository extRefRepo;

    @Test
    void resavingSameExtRefsMustNotDuplicate() {
        String code = "TEST-EXTREF-" + System.currentTimeMillis();
        Map<String, Object> refs1 = Map.of("sourceSys", "WCS", "extCode", "TEST-STA-001");
        Map<String, Object> refs2 = Map.of("sourceSys", "WCS", "extCode", "TEST-STA-002");

        Map<String, Object> created = equipmentService.save(null, Map.of(
                "code", code, "name", "映射回归测试线",
                "extRefs", List.of(refs1, refs2)));
        Long id = ((Number) created.get("id")).longValue();
        assertEquals(2, extRefRepo.findByEquipmentId(id).size());

        // 再次保存：映射原样带回、只改名称——修复前这一步撞唯一键
        Map<String, Object> updated = equipmentService.save(id, Map.of(
                "code", code, "name", "映射回归测试线-改名",
                "extRefs", List.of(refs1, refs2)));
        assertEquals("映射回归测试线-改名", updated.get("name"));
        assertEquals(2, extRefRepo.findByEquipmentId(id).size());

        // 删掉一个映射：先删后插同样要走批量删除
        equipmentService.save(id, Map.of(
                "code", code, "name", "映射回归测试线-改名",
                "extRefs", List.of(refs1)));
        assertEquals(1, extRefRepo.findByEquipmentId(id).size());
    }
}
