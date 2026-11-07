package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
public record RoleSaveReq(@NotBlank String code,@NotBlank String name,String description,Boolean enabled,Set<String> permissions){}
