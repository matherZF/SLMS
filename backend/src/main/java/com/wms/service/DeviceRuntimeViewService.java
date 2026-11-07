package com.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dto.DeviceRuntimeViewVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "device.datasource", name = "enabled", havingValue = "true")
public class DeviceRuntimeViewService {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$.]+");
    private static final String INTERFACE_CODE = "DEVICE_RUNTIME_QUERY";
    private static final String INTERFACE_NAME = "设备运行数据查询";
    private final JdbcTemplate jdbcTemplate;
    private final String viewName;
    private final InterfaceLogService interfaceLogService;
    private final ObjectMapper objectMapper;

    /** 视图是否提供 total_piece_qty（累计处理件数）列；null = 还没探测过 */
    private volatile Boolean pieceQtySupported;

    public DeviceRuntimeViewService(
            @Qualifier("deviceJdbcTemplate") JdbcTemplate deviceJdbcTemplate,
            @Value("${device.datasource.view-name}") String viewName,
            InterfaceLogService interfaceLogService,
            ObjectMapper objectMapper) {
        if (!SAFE_IDENTIFIER.matcher(viewName).matches()) {
            throw new IllegalArgumentException("设备视图名称不合法");
        }
        this.jdbcTemplate = deviceJdbcTemplate;
        this.viewName = viewName;
        this.interfaceLogService = interfaceLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 探测视图有没有件数列。设备系统不一定同步升级视图，
     * 所以这里做"可选列"兼容：没有就不查，返回 null 让上层走每日汇总表兜底。
     */
    private boolean supportsPieceQty() {
        Boolean cached = pieceQtySupported;
        if (cached != null) return cached;
        boolean supported;
        try {
            jdbcTemplate.query("select total_piece_qty from " + viewName + " where 1 = 0", rs -> {
            });
            supported = true;
        } catch (RuntimeException e) {
            supported = false;
        }
        pieceQtySupported = supported;
        return supported;
    }

    private String selectColumns() {
        return supportsPieceQty()
                ? "equipment_no, total_running_hours, total_task_count, total_piece_qty"
                : "equipment_no, total_running_hours, total_task_count";
    }

    private DeviceRuntimeViewVO map(java.sql.ResultSet rs, boolean withPieceQty) throws java.sql.SQLException {
        Long pieceQty = null;
        if (withPieceQty) {
            long value = rs.getLong("total_piece_qty");
            pieceQty = rs.wasNull() ? null : value;
        }
        return new DeviceRuntimeViewVO(
                rs.getString("equipment_no"),
                rs.getBigDecimal("total_running_hours"),
                rs.getLong("total_task_count"),
                pieceQty);
    }

    public List<DeviceRuntimeViewVO> findAll() {
        boolean withPieceQty = supportsPieceQty();
        String sql = "select " + selectColumns() + " from " + viewName;
        long started = System.currentTimeMillis();
        try {
            List<DeviceRuntimeViewVO> rows = jdbcTemplate.query(sql,
                    (rs, rowNum) -> map(rs, withPieceQty));
            record(sql, toJson(rows), true, null, started);
            return rows;
        } catch (RuntimeException e) {
            record(sql, null, false, e.getMessage(), started);
            throw e;
        }
    }

    public DeviceRuntimeViewVO findByEquipmentNo(String equipmentNo) {
        boolean withPieceQty = supportsPieceQty();
        String sql = "select " + selectColumns() + " from " + viewName + " where equipment_no = ?";
        long started = System.currentTimeMillis();
        try {
            DeviceRuntimeViewVO row = jdbcTemplate.query(sql,
                            (rs, rowNum) -> map(rs, withPieceQty), equipmentNo)
                    .stream().findFirst().orElse(null);
            record(sql, toJson(row), true, null, started);
            return row;
        } catch (RuntimeException e) {
            record(sql, null, false, e.getMessage(), started);
            throw e;
        }
    }

    private void record(String request, String response, boolean success, String error, long started) {
        interfaceLogService.recordOutbound(INTERFACE_CODE, INTERFACE_NAME, "JDBC",
                "设备运行数据库/" + viewName, request, response, success, error, null,
                System.currentTimeMillis() - started);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
