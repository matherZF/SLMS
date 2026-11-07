package com.wms.dto;
import java.time.Instant;
import java.util.Set;
public record RoleDTO(Long id,String code,String name,String description,boolean builtin,boolean enabled,Set<String> permissions,Instant createdAt){}
