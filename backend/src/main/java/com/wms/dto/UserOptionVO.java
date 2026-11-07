package com.wms.dto;

/**
 * 用户下拉选项。
 *
 * <p>只暴露显示名和用户名，不含角色、密码等敏感字段，供「实际领用人」这类选择控件使用。
 * 用户管理接口 /api/users/** 需要 system:user 权限，普通领用人打不开，所以单独开一个只读接口。
 */
public record UserOptionVO(String username, String displayName) {}
