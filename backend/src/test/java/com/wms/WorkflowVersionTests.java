package com.wms;

import com.wms.dto.WorkflowSaveReq;
import com.wms.entity.ApprovalStatus;
import com.wms.repository.WorkflowDefinitionRepository;
import com.wms.service.ApprovalService;
import com.wms.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkflowVersionTests {
    @Autowired WorkflowService workflowService;
    @Autowired WorkflowDefinitionRepository workflowRepository;
    @Autowired ApprovalService approvalService;

    @Test
    void publishingCreatesNewVersionAndPreservesOldDefinition() {
        var old = workflowRepository.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc("OUTBOUND").orElseThrow();
        Long oldId = old.getId();
        String oldNodeName = old.getNodes().get(0).getName();

        var saved = workflowService.save(oldId, new WorkflowSaveReq(
                "OUTBOUND", "领用分级审批", true, "ANY", List.of(), 10, null,
                List.of(new WorkflowSaveReq.Node(1, "新版审批节点", "ROLE", "APPROVER"))));

        assertEquals(2, saved.version());
        assertNotEquals(oldId, saved.id());
        var preserved = workflowRepository.findById(oldId).orElseThrow();
        assertFalse(preserved.isEnabled());
        assertEquals(oldNodeName, preserved.getNodes().get(0).getName());
        assertEquals("新版审批节点", workflowService.active("OUTBOUND").orElseThrow().getNodes().get(0).getName());
    }

    @Test
    void unmatchedOutboundConditionCreatesTraceableAutoDecision() {
        var old = workflowRepository.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc("OUTBOUND").orElseThrow();
        workflowService.save(old.getId(), new WorkflowSaveReq(
                "OUTBOUND", "数量审批", true, "ANY", List.of(), 10, new BigDecimal("1000"),
                List.of(new WorkflowSaveReq.Node(1, "数量审批", "ROLE", "APPROVER"))));

        var record = approvalService.submitOutbound(999001L, "TEST-OUT-001", "001",
                new ApprovalService.WorkflowContext(Set.of("普通备件"), 2, new BigDecimal("100")));

        assertEquals(ApprovalStatus.APPROVED, record.getStatus());
        assertEquals("SYSTEM", record.getApprover());
        assertNotNull(record.getMatchReason());
        assertTrue(record.getMatchReason().contains("未命中"));
        assertTrue(record.getMatchReason().contains("系统自动放行"));
    }
}
