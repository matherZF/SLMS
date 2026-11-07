package com.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateReq(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        String displayName,
        @NotBlank String role
) {
}
