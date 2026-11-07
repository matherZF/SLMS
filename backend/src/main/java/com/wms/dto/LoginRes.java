package com.wms.dto;

public record LoginRes(String token, Long userId, String username, String displayName, String role, String roleName, java.util.Set<String> permissions) {
}
