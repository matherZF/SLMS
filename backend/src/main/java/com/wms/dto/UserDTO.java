package com.wms.dto;

import java.time.Instant;

public record UserDTO(Long id, String username, String displayName, String role, String roleName, java.util.Set<String> permissions, Boolean enabled, Instant createdAt) {
}
