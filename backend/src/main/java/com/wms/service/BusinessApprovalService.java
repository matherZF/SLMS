package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.InventoryAdjustmentReq;
import com.wms.dto.ReturnActionPayload;
import com.wms.entity.PendingBusinessAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BusinessApprovalService {
    private final ApprovalService approvals;
    private final PendingBusinessActionService actions;
    private final OutboundService outbound;
    private final InventoryService inventory;
    private final StocktakeService stocktake;

    public BusinessApprovalService(ApprovalService approvals, PendingBusinessActionService actions,
                                   OutboundService outbound, InventoryService inventory, StocktakeService stocktake) {
        this.approvals=approvals; this.actions=actions; this.outbound=outbound;
        this.inventory=inventory; this.stocktake=stocktake;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> review(String rawType, Long businessId, boolean approved, String opinion, String operator) {
        String type=rawType.trim().toUpperCase();
        if ("OUTBOUND".equals(type)) return outbound.review(businessId, approved, opinion);
        if (!java.util.Set.of("RETURN","SCRAP","STOCKTAKE_DIFF").contains(type)) throw new BizException("不支持的审批业务类型");
        ApprovalService.HandleResult result=approvals.handle(type,businessId,operator,approved,opinion);
        Map<String,Object> response=new LinkedHashMap<>();
        response.put("completed",result.completed()); response.put("currentNode",result.currentNode());
        response.put("nextNode",result.nextNode());
        if (!result.completed()) return response;
        PendingBusinessAction action=actions.pending(businessId);
        if (!approved) {
            actions.finish(action,"REJECTED");
            if ("STOCKTAKE_DIFF".equals(type)) stocktake.rejectReview(action.getSourceId(),operator);
            response.put("status","REJECTED");
            return response;
        }
        switch (type) {
            case "RETURN" -> outbound.executeApprovedReturn(actions.payload(action, ReturnActionPayload.class),operator);
            case "SCRAP" -> inventory.executeApprovedScrap(actions.payload(action, InventoryAdjustmentReq.class),operator);
            case "STOCKTAKE_DIFF" -> stocktake.executeApprovedDifference(action.getSourceId(),operator);
            default -> throw new BizException("不支持的审批业务类型");
        }
        actions.finish(action,"APPROVED");
        response.put("status","APPROVED");
        return response;
    }
}
