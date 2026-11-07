package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class WorkflowService {
    private final WorkflowDefinitionRepository repo;
    private final RoleRepository roles;
    private final UserRepository users;
    private final OperationAuditService auditService;

    public WorkflowService(WorkflowDefinitionRepository repo, RoleRepository roles, UserRepository users,
                           OperationAuditService auditService) {
        this.repo = repo; this.roles = roles; this.users = users;
        this.auditService = auditService;
    }

    public List<WorkflowDTO> list() {
        Map<String, WorkflowDefinition> latest = new LinkedHashMap<>();
        repo.findAll().stream()
                .sorted(Comparator.comparing(WorkflowDefinition::getVersion).reversed())
                .forEach(x -> latest.putIfAbsent(x.getBusinessType(), x));
        return latest.values().stream().map(this::dto).toList();
    }

    public Optional<WorkflowDefinition> active(String type) {
        return repo.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc(type);
    }

    @Transactional
    public WorkflowDTO save(Long id, WorkflowSaveReq q) {
        String type = q.businessType().trim().toUpperCase();
        WorkflowDefinition previous = id == null ? null : repo.findById(id)
                .orElseThrow(() -> new BizException("流程不存在"));
        if (previous != null && !previous.getBusinessType().equals(type)) throw new BizException("业务类型不能修改");
        if (id == null && repo.findFirstByBusinessTypeOrderByVersionDesc(type).isPresent()) throw new BizException("该业务已配置流程");

        String mode = q.triggerMode() == null ? "ALWAYS" : q.triggerMode().toUpperCase();
        if (!Set.of("ALWAYS", "ANY", "ALL").contains(mode)) throw new BizException("触发条件模式无效");
        if (q.minTotalQty() != null && q.minTotalQty() < 0) throw new BizException("数量阈值不能小于0");
        if (q.minTotalAmount() != null && q.minTotalAmount().signum() < 0) throw new BizException("金额阈值不能小于0");
        if (q.nodes() == null || q.nodes().isEmpty()) throw new BizException("流程至少需要一个审批节点");

        int version = repo.findFirstByBusinessTypeOrderByVersionDesc(type)
                .map(x -> x.getVersion() + 1).orElse(1);
        repo.findByBusinessTypeOrderByVersionDesc(type).stream().filter(WorkflowDefinition::isEnabled)
                .forEach(x -> { x.setEnabled(false); repo.save(x); });

        WorkflowDefinition d = new WorkflowDefinition();
        d.setBusinessType(type); d.setVersion(version); d.setName(q.name().trim());
        d.setEnabled(q.enabled() == null || q.enabled()); d.setTriggerMode(mode);
        d.setTriggerCategories(q.triggerCategories() == null ? null : String.join(",", q.triggerCategories()));
        d.setMinTotalQty(q.minTotalQty()); d.setMinTotalAmount(q.minTotalAmount());
        int seq = 1;
        for (var n : q.nodes()) {
            String at = n.approverType().toUpperCase();
            if (!Set.of("ROLE", "USER").contains(at)) throw new BizException("审批人类型无效");
            if (at.equals("ROLE") && roles.findByCode(n.approverValue()).isEmpty()) throw new BizException("审批角色不存在");
            if (at.equals("USER") && users.findByUsername(n.approverValue()).isEmpty()) throw new BizException("审批用户不存在");
            WorkflowNode x = new WorkflowNode(); x.setDefinition(d); x.setSequence(seq++);
            x.setName(n.name().trim()); x.setApproverType(at); x.setApproverValue(n.approverValue()); d.getNodes().add(x);
        }
        WorkflowDefinition saved = repo.save(d);
        String action = previous == null ? "CREATE" : "UPDATE";
        auditService.record("WORKFLOW", saved.getId(), type, action,
                previous == null ? null : "v" + previous.getVersion(), "v" + saved.getVersion(),
                (previous == null ? "创建流程配置 [" : "更新流程配置 [") + type + "/" + saved.getName()
                        + "]，版本 v" + saved.getVersion() + "，节点数 " + saved.getNodes().size());
        return dto(saved);
    }

    private WorkflowDTO dto(WorkflowDefinition d) {
        return new WorkflowDTO(d.getId(), d.getBusinessType(), d.getVersion(), d.getPublishedAt(), d.getName(), d.isEnabled(),
                d.getTriggerMode(), d.getTriggerCategories() == null || d.getTriggerCategories().isBlank() ? List.of() : Arrays.asList(d.getTriggerCategories().split(",")),
                d.getMinTotalQty(), d.getMinTotalAmount(), d.getNodes().stream().map(n ->
                new WorkflowDTO.Node(n.getId(), n.getSequence(), n.getName(), n.getApproverType(), n.getApproverValue(), label(n))).toList());
    }

    private String label(WorkflowNode n) {
        if (n.getApproverType().equals("ROLE")) return roles.findByCode(n.getApproverValue()).map(Role::getName).orElse(n.getApproverValue());
        return users.findByUsername(n.getApproverValue()).map(User::getDisplayName).orElse(n.getApproverValue());
    }
}
