package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.InterfaceLogSummaryVO;
import com.wms.dto.InterfaceLogVO;
import com.wms.entity.InterfaceLog;
import com.wms.repository.InterfaceLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class InterfaceLogService {
    public static final String INBOUND = "INBOUND";
    public static final String OUTBOUND = "OUTBOUND";
    private static final int MAX_PAYLOAD = 10000;
    private static final Logger log = LoggerFactory.getLogger(InterfaceLogService.class);

    private final InterfaceLogRepository repository;

    public InterfaceLogService(InterfaceLogRepository repository) { this.repository = repository; }

    /**
     * 记录一次接口交互。日志失败不影响主流程，只输出告警。
     */
    public void record(String direction, String interfaceCode, String interfaceName, String channel, String peer,
                       String requestBody, String responseBody, boolean success, String errorMessage,
                       String traceId, Long durationMs) {
        try {
            InterfaceLog item = new InterfaceLog();
            item.setDirection(INBOUND.equals(direction) ? INBOUND : OUTBOUND);
            item.setInterfaceCode(interfaceCode);
            item.setInterfaceName(interfaceName);
            item.setChannel(channel);
            item.setPeer(peer);
            item.setRequestBody(truncate(requestBody));
            item.setResponseBody(truncate(responseBody));
            item.setSuccess(success);
            item.setErrorMessage(truncate(errorMessage, 500));
            item.setTraceId(traceId);
            item.setDurationMs(durationMs);
            repository.save(item);
        } catch (Exception e) {
            log.warn("接口日志写入失败 code={} direction={}: {}", interfaceCode, direction, e.getMessage());
        }
    }

    public void recordInbound(String interfaceCode, String interfaceName, String channel, String peer,
                              String requestBody, String responseBody, boolean success, String errorMessage,
                              String traceId, Long durationMs) {
        record(INBOUND, interfaceCode, interfaceName, channel, peer, requestBody, responseBody,
                success, errorMessage, traceId, durationMs);
    }

    public void recordOutbound(String interfaceCode, String interfaceName, String channel, String peer,
                               String requestBody, String responseBody, boolean success, String errorMessage,
                               String traceId, Long durationMs) {
        record(OUTBOUND, interfaceCode, interfaceName, channel, peer, requestBody, responseBody,
                success, errorMessage, traceId, durationMs);
    }

    public Page<InterfaceLogSummaryVO> page(String direction, String interfaceCode, Boolean success,
                                            String keyword, int page, int size) {
        String dir = StringUtils.hasText(direction) ? direction.trim().toUpperCase() : null;
        String code = StringUtils.hasText(interfaceCode) ? interfaceCode.trim() : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;

        Specification<InterfaceLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (dir != null) predicates.add(cb.equal(root.get("direction"), dir));
            if (code != null) predicates.add(cb.equal(root.get("interfaceCode"), code));
            if (success != null) predicates.add(cb.equal(root.get("success"), success));
            if (kw != null) {
                String like = "%" + kw + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("interfaceCode")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("interfaceName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("peer"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("traceId"), "")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("id").descending())).map(this::toSummary);
    }

    public InterfaceLogVO detail(Long id) {
        InterfaceLog x = repository.findById(id).orElseThrow(() -> new BizException("接口日志不存在"));
        return new InterfaceLogVO(x.getId(), x.getDirection(), x.getInterfaceCode(), x.getInterfaceName(),
                x.getChannel(), x.getPeer(), x.getRequestBody(), x.getResponseBody(), x.isSuccess(),
                x.getErrorMessage(), x.getTraceId(), x.getDurationMs(), x.getCreatedAt());
    }

    private InterfaceLogSummaryVO toSummary(InterfaceLog x) {
        return new InterfaceLogSummaryVO(x.getId(), x.getDirection(), x.getInterfaceCode(), x.getInterfaceName(),
                x.getChannel(), x.getPeer(), x.isSuccess(), x.getErrorMessage(), x.getTraceId(),
                x.getDurationMs(), x.getCreatedAt());
    }

    private String truncate(String value) { return truncate(value, MAX_PAYLOAD); }

    private static final String TRUNCATED_SUFFIX = "…(已截断)";

    /**
     * 按字段上限截断。
     * <p>
     * 原实现是 {@code substring(0, max) + suffix}，截断后的长度是 max + 后缀长度，
     * 反而超出 varchar 上限：报文超长时写日志这一步就抛异常，而这里正是"记录失败原因"的路径，
     * 结果整条接口日志丢失——最需要日志的时候恰好没日志。这里把后缀长度算进上限。
     */
    private String truncate(String value, int max) {
        if (value == null) return null;
        if (value.length() <= max) return value;
        int keep = Math.max(0, max - TRUNCATED_SUFFIX.length());
        return value.substring(0, keep) + TRUNCATED_SUFFIX;
    }
}
