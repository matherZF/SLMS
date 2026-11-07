package com.wms.service;

import com.wms.dto.OperationAuditLogVO;
import com.wms.entity.OperationAuditLog;
import com.wms.repository.OperationAuditLogRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

@Service
public class OperationAuditService {
    private final OperationAuditLogRepository repository;
    public OperationAuditService(OperationAuditLogRepository repository) { this.repository = repository; }

    public void record(String businessType, Long businessId, String businessNo, String action,
                       String fromStatus, String toStatus, String operator, String detail) {
        OperationAuditLog log = new OperationAuditLog();
        log.setBusinessType(businessType);
        // wms_operation_audit_log.business_id 是 NOT NULL；AUTH 这类非单据审计（如登录失败）没有业务 id，
        // 传 null 会让审计写入抛异常，把"用户名或密码错误"顶成系统错误。统一用 0 表示"无关联业务单据"。
        log.setBusinessId(businessId == null ? 0L : businessId);
        log.setBusinessNo(businessNo);
        log.setAction(action); log.setFromStatus(fromStatus); log.setToStatus(toStatus);
        log.setOperator(StringUtils.hasText(operator) ? operator : currentOperator()); log.setDetail(detail);
        repository.save(log);
    }

    public void record(String businessType, Long businessId, String businessNo, String action,
                       String fromStatus, String toStatus, String detail) {
        record(businessType, businessId, businessNo, action, fromStatus, toStatus, null, detail);
    }

    public List<OperationAuditLogVO> list(String businessType, Long businessId) {
        return repository.findByBusinessTypeAndBusinessIdOrderByIdAsc(businessType, businessId).stream()
                .map(this::toVO).toList();
    }

    public Page<OperationAuditLogVO> page(String businessType, String keyword, int page, int size) {
        String type = StringUtils.hasText(businessType) ? businessType.trim().toUpperCase() : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return repository.search(type, kw, PageRequest.of(page, size, Sort.by("id").descending())).map(this::toVO);
    }

    private OperationAuditLogVO toVO(OperationAuditLog x) {
        return new OperationAuditLogVO(x.getId(), x.getBusinessType(), x.getBusinessId(), x.getBusinessNo(),
                x.getAction(), x.getFromStatus(), x.getToStatus(), x.getOperator(), x.getDetail(), x.getCreatedAt());
    }

    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getPrincipal() == null ? "system" : String.valueOf(auth.getPrincipal());
    }
}
