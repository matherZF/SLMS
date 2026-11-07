package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.ApprovalRecordVO;
import com.wms.dto.ApprovalTaskVO;
import com.wms.entity.*;
import com.wms.repository.ApprovalRecordRepository;
import com.wms.repository.RoleRepository;
import com.wms.repository.WorkflowDefinitionRepository;
import com.wms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;

@Service
public class ApprovalService {
    /** 业务审批按钮权限码：审批中心里所有业务（领用/退库/报废/盘点差异）都由它把关 */
    private static final String APPROVE_AUTHORITY = "outbound:approve";
    private final ApprovalRecordRepository repo;
    private final WorkflowDefinitionRepository workflowRepo; private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final ApprovalSnapshotService snapshotService;
    private final long timeoutHours;
    public ApprovalService(ApprovalRecordRepository repo,WorkflowDefinitionRepository workflowRepo,UserRepository userRepo,
                           RoleRepository roleRepo, ApprovalSnapshotService snapshotService,
                           @Value("${app.approval.timeout-hours:24}") long timeoutHours) {
        this.repo = repo;this.workflowRepo=workflowRepo;this.userRepo=userRepo;this.roleRepo=roleRepo;
        this.snapshotService = snapshotService;
        this.timeoutHours = Math.max(1, timeoutHours);
    }
    public record HandleResult(boolean completed,String currentNode,String nextNode){}
    public record WorkflowContext(Set<String> categories,int totalQty,BigDecimal totalAmount){}
    private record MatchResult(boolean matched,String reason){}

    public ApprovalRecord submit(String type, Long id, String no, String applicant) {
        List<ApprovalRecord> history = repo.findByBusinessTypeAndBusinessIdOrderByRoundNoAscIdAsc(type, id);
        if (history.stream().anyMatch(a -> a.getStatus() == ApprovalStatus.PENDING)) throw new BizException("该单据已有待处理审批");
        int round=history.stream().mapToInt(ApprovalRecord::getRoundNo).max().orElse(0)+1;
        var workflow=workflowRepo.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc(type);
        if(workflow.isPresent()&&!workflow.get().getNodes().isEmpty())return createAndAdvance(type,id,no,applicant,round,workflow.get(),0,"流程固定触发");
        return create(type,id,no,applicant,round,null,null);
    }

    /** 领用条件不命中时返回 null，表示无需审批；命中后自动跳过与申请人身份相同的节点。 */
    public ApprovalRecord submitOutbound(Long id,String no,String applicant,WorkflowContext context){
        List<ApprovalRecord> history=repo.findByBusinessTypeAndBusinessIdOrderByRoundNoAscIdAsc("OUTBOUND",id);
        int round=history.stream().mapToInt(ApprovalRecord::getRoundNo).max().orElse(0)+1;
        var workflow=workflowRepo.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc("OUTBOUND");
        if(workflow.isEmpty())return createAutoDecision("OUTBOUND",id,no,applicant,round,"未配置启用的领用审批流程，系统自动放行");
        MatchResult match=match(workflow.get(),context);
        if(!match.matched())return createAutoDecision("OUTBOUND",id,no,applicant,round,match.reason());
        return createAndAdvance("OUTBOUND",id,no,applicant,round,workflow.get(),0,match.reason());
    }

    public HandleResult handle(String type, Long id, String approver, boolean approved, String opinion) {
        ApprovalRecord a = current(type, id);
        if (a.getApplicant().equalsIgnoreCase(approver)) throw new BizException("申请人与审批人不能为同一人");
        var user=userRepo.findByUsername(approver).orElseThrow(()->new BizException("审批用户不存在"));
        if(a.getApproverType()!=null){boolean assigned="USER".equals(a.getApproverType())?approver.equals(a.getApproverValue()):user.getRole().equals(a.getApproverValue());if(!assigned)throw new BizException("当前审批节点未分配给你");}
        // 没有 approverType 的记录来自"流程未配置/被停用"时的兜底建单（见 submit），
        // 这种记录没有指定审批人，若不额外限制，任何登录用户都能审批任意申请。
        else if(!permissionsOf(user).contains(APPROVE_AUTHORITY)) throw new BizException("当前审批节点未分配审批人，需具备业务审批权限才能处理");
        if (!approved && !StringUtils.hasText(opinion)) throw new BizException("驳回时必须填写审批意见");
        a.setApprover(approver); a.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        a.setOpinion(StringUtils.hasText(opinion) ? opinion.trim() : "审批通过"); a.setHandledAt(Instant.now()); repo.save(a);
        if(!approved)return new HandleResult(true,a.getNodeName(),null);
        if(a.getWorkflowDefinitionId()!=null){var workflow=workflowRepo.findById(a.getWorkflowDefinitionId()).orElse(null);if(workflow!=null){int nextIndex=-1;for(int i=0;i<workflow.getNodes().size();i++)if(workflow.getNodes().get(i).getSequence()>a.getNodeSequence()){nextIndex=i;break;}if(nextIndex>=0){ApprovalRecord next=createAndAdvance(type,id,a.getBusinessNo(),a.getApplicant(),a.getRoundNo(),workflow,nextIndex,a.getMatchReason());if(next.getStatus()==ApprovalStatus.PENDING)return new HandleResult(false,a.getNodeName(),next.getNodeName());return new HandleResult(true,a.getNodeName(),null);}}}
        return new HandleResult(true,a.getNodeName(),null);
    }

    public void cancel(String type, Long id, String operator, String reason) {
        List<ApprovalRecord> rows = repo.findCurrentForUpdate(type, id, ApprovalStatus.PENDING);
        if (!rows.isEmpty()) { ApprovalRecord a=rows.get(0); a.setApprover(operator); a.setStatus(ApprovalStatus.CANCELLED);
            a.setOpinion(StringUtils.hasText(reason) ? reason.trim() : "申请已取消"); a.setHandledAt(Instant.now()); repo.save(a); }
    }

    @Transactional(readOnly = true)
    public List<ApprovalRecordVO> list(String type, Long id) {
        return repo.findByBusinessTypeAndBusinessIdOrderByRoundNoAscIdAsc(type,id).stream().map(a ->
                new ApprovalRecordVO(a.getId(),a.getBusinessType(),a.getBusinessId(),a.getBusinessNo(),a.getRoundNo(),
                            a.getApplicant(),a.getApprover(),a.getStatus().name(),a.getOpinion(),a.getSubmittedAt(),a.getHandledAt(),a.getNodeSequence(),a.getNodeName(),a.getApproverType(),a.getApproverValue(),a.getWorkflowVersion(),a.getMatchReason())).toList();
    }
    private ApprovalRecord current(String type, Long id) {
        List<ApprovalRecord> rows=repo.findCurrentForUpdate(type,id,ApprovalStatus.PENDING);
        if (rows.isEmpty()) throw new BizException("未找到待处理的审批记录");
        return rows.get(0);
    }
    @Transactional(readOnly=true) public boolean canHandle(String type,Long id,String username){var rows=repo.findByBusinessTypeAndBusinessIdOrderByRoundNoAscIdAsc(type,id);var a=rows.stream().filter(x->x.getStatus()==ApprovalStatus.PENDING).reduce((x,y)->y).orElse(null);if(a==null)return false;var u=userRepo.findByUsername(username).orElse(null);if(u==null||a.getApplicant().equalsIgnoreCase(username))return false;
        // 无指定审批人的记录（流程未配置/被停用）只能由持有业务审批权限的人处理，否则任务列表会把它推给所有人
        if(a.getApproverType()==null)return permissionsOf(u).contains(APPROVE_AUTHORITY);
        return "USER".equals(a.getApproverType())?username.equals(a.getApproverValue()):u.getRole().equals(a.getApproverValue());}

    /** 取用户所属角色的权限码集合；角色不存在（历史脏数据）时返回空集合，取最保守的结果 */
    private Set<String> permissionsOf(User user){
        if(user.getRole()==null||user.getRole().isBlank())return Set.of();
        return roleRepo.findByCode(user.getRole()).map(Role::getPermissions).orElse(Set.of());
    }
    @Transactional(readOnly=true)
    public List<ApprovalTaskVO> tasks(String username){
        return repo.findByStatusOrderBySubmittedAtAsc(ApprovalStatus.PENDING).stream()
                .filter(a->canHandle(a.getBusinessType(),a.getBusinessId(),username))
                .map(a->{
                    Instant dueAt=a.getSubmittedAt().plus(Duration.ofHours(timeoutHours));
                    long waiting=Math.max(0,Duration.between(a.getSubmittedAt(),Instant.now()).toHours());
                    var snapshot=snapshotService.build(a.getBusinessType(),a.getBusinessId());
                    return new ApprovalTaskVO(a.getBusinessType(),a.getBusinessId(),a.getBusinessNo(),a.getApplicant(),
                            a.getNodeName(),a.getApproverType(),a.getApproverValue(),a.getWorkflowVersion(),a.getMatchReason(),
                            a.getSubmittedAt(),dueAt,waiting,Instant.now().isAfter(dueAt),snapshot);
                }).toList();
    }
    private ApprovalRecord create(String type,Long id,String no,String applicant,int round,WorkflowDefinition workflow,WorkflowNode node,String matchReason){ApprovalRecord a=new ApprovalRecord();a.setBusinessType(type);a.setBusinessId(id);a.setBusinessNo(no);a.setRoundNo(round);a.setApplicant(applicant);a.setStatus(ApprovalStatus.PENDING);a.setSubmittedAt(Instant.now());a.setMatchReason(matchReason);if(workflow!=null){a.setWorkflowDefinitionId(workflow.getId());a.setWorkflowVersion(workflow.getVersion());a.setNodeSequence(node.getSequence());a.setNodeName(node.getName());a.setApproverType(node.getApproverType());a.setApproverValue(node.getApproverValue());}else{a.setNodeSequence(1);a.setNodeName("审批");}return repo.save(a);}
    private ApprovalRecord create(String type,Long id,String no,String applicant,int round,WorkflowDefinition workflow,WorkflowNode node){return create(type,id,no,applicant,round,workflow,node,null);}
    private ApprovalRecord createAndAdvance(String type,Long id,String no,String applicant,int round,WorkflowDefinition workflow,int start,String matchReason){ApprovalRecord last=null;for(int i=start;i<workflow.getNodes().size();i++){WorkflowNode node=workflow.getNodes().get(i);last=create(type,id,no,applicant,round,workflow,node,matchReason);if(!sameIdentity(applicant,node))return last;last.setApprover(applicant);last.setStatus(ApprovalStatus.APPROVED);last.setOpinion("系统自动通过：申请人与审批节点身份相同");last.setHandledAt(Instant.now());repo.save(last);}return last;}
    private ApprovalRecord createAndAdvance(String type,Long id,String no,String applicant,int round,WorkflowDefinition workflow,int start){return createAndAdvance(type,id,no,applicant,round,workflow,start,"流程固定触发");}
    private ApprovalRecord createAutoDecision(String type,Long id,String no,String applicant,int round,String reason){ApprovalRecord a=create(type,id,no,applicant,round,null,null,reason);a.setNodeName("系统判定");a.setApprover("SYSTEM");a.setStatus(ApprovalStatus.APPROVED);a.setOpinion(reason);a.setHandledAt(Instant.now());return repo.save(a);}
    private boolean sameIdentity(String applicant,WorkflowNode node){if("USER".equals(node.getApproverType()))return applicant.equals(node.getApproverValue());return userRepo.findByUsername(applicant).map(u->u.getRole().equals(node.getApproverValue())).orElse(false);}
    private MatchResult match(WorkflowDefinition w,WorkflowContext c){if("ALWAYS".equals(w.getTriggerMode())||w.getTriggerMode()==null)return new MatchResult(true,"流程设置为始终审批");if(c==null)return new MatchResult(true,"缺少业务条件上下文，按需审批处理");List<Boolean> checks=new java.util.ArrayList<>();List<String> details=new java.util.ArrayList<>();if(w.getTriggerCategories()!=null&&!w.getTriggerCategories().isBlank()){Set<String> expected=Set.of(w.getTriggerCategories().split(","));boolean hit=c.categories()!=null&&c.categories().stream().anyMatch(expected::contains);checks.add(hit);details.add("分类"+(hit?"命中":"未命中")+"[配置="+String.join("/",expected)+"，申请="+(c.categories()==null?"-":String.join("/",c.categories()))+"]");}if(w.getMinTotalQty()!=null){boolean hit=c.totalQty()>=w.getMinTotalQty();checks.add(hit);details.add("数量"+(hit?"命中":"未命中")+"[申请="+c.totalQty()+"，阈值="+w.getMinTotalQty()+"]");}if(w.getMinTotalAmount()!=null){boolean hit=c.totalAmount()!=null&&c.totalAmount().compareTo(w.getMinTotalAmount())>=0;checks.add(hit);details.add("金额"+(hit?"命中":"未命中")+"[申请="+(c.totalAmount()==null?"0":c.totalAmount())+"，阈值="+w.getMinTotalAmount()+"]");}if(checks.isEmpty())return new MatchResult(true,"未配置具体条件，按始终审批处理");boolean matched="ALL".equals(w.getTriggerMode())?checks.stream().allMatch(Boolean::booleanValue):checks.stream().anyMatch(Boolean::booleanValue);return new MatchResult(matched,(matched?"命中":"未命中")+"流程条件（"+("ALL".equals(w.getTriggerMode())?"全部满足":"任一满足")+"）："+String.join("；",details)+(matched?"":"，系统自动放行"));}
}
