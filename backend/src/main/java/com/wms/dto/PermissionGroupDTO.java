package com.wms.dto;
import java.util.List;
public record PermissionGroupDTO(String pageCode,String pageName,List<PermissionItemDTO> actions){
 public record PermissionItemDTO(String code,String name){}
}
