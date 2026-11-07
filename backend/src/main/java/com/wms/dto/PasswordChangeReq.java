package com.wms.dto;

/** 修改密码请求（当前登录用户自助操作） */
public record PasswordChangeReq(String oldPassword, String newPassword) {
}
