package com.wms.dto;

public record UserUpdateReq(String displayName, String role, Boolean enabled, String password) {
}
