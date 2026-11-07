package com.wms.service;
import java.util.*;
public final class PermissionCatalog {
 private PermissionCatalog(){}
 public static final Map<String,String> ALL=new LinkedHashMap<>();
 public static final Map<String,String> PARENTS=new LinkedHashMap<>();
 static {
  ALL.put("page:workbench","页面/工作台"); ALL.put("page:inbound","页面/入库与上架"); ALL.put("page:outbound","页面/领用与出库"); ALL.put("page:approval","页面/审批中心");
  ALL.put("page:inventory","页面/库存台账"); ALL.put("page:usage","页面/使用台账"); ALL.put("page:stocktake","页面/库存盘点"); ALL.put("page:alerts","页面/预警中心"); ALL.put("page:master","页面/基础数据"); ALL.put("page:system","页面/系统管理");
  ALL.put("inbound:create","按钮/新建入库单"); ALL.put("inbound:putaway","按钮/上架作业"); ALL.put("outbound:create","按钮/新建领用单"); ALL.put("outbound:picking","按钮/拣货出库"); ALL.put("outbound:manage","按钮/领用单代管"); ALL.put("outbound:approve","按钮/业务审批");
  ALL.put("inventory:transfer","按钮/库存移库"); ALL.put("inventory:scrap","按钮/备件报废"); ALL.put("stocktake:create","按钮/创建盘点"); ALL.put("stocktake:complete","按钮/完成盘点"); ALL.put("master:edit","按钮/维护基础数据");
  ALL.put("system:user","按钮/用户管理"); ALL.put("system:role","按钮/角色管理"); ALL.put("system:audit","按钮/操作审计"); ALL.put("system:dict","按钮/数据字典");
  ALL.put("system:config","按钮/系统配置");
  ALL.put("system:workflow","按钮/流程管理"); ALL.put("system:interface","按钮/接口管理");
  parent("inbound:create","page:inbound"); parent("inbound:putaway","page:inbound");
  parent("outbound:create","page:outbound"); parent("outbound:picking","page:outbound"); parent("outbound:manage","page:outbound"); parent("outbound:approve","page:approval");
  parent("inventory:transfer","page:inventory"); parent("inventory:scrap","page:inventory");
  parent("stocktake:create","page:stocktake"); parent("stocktake:complete","page:stocktake"); parent("master:edit","page:master");
  parent("system:user","page:system"); parent("system:role","page:system"); parent("system:audit","page:system"); parent("system:dict","page:system");
  parent("system:config","page:system");
  parent("system:workflow","page:system"); parent("system:interface","page:system");
 }
 private static void parent(String action,String page){PARENTS.put(action,page);}
 public static Set<String> validate(Set<String> values){Set<String> result=new LinkedHashSet<>(values==null?Set.of():values);if(!ALL.keySet().containsAll(result))throw new com.wms.common.BizException("包含无效权限码");for(var e:PARENTS.entrySet())if(result.contains(e.getKey())&&!result.contains(e.getValue()))throw new com.wms.common.BizException("按钮权限必须隶属于已授权页面："+ALL.get(e.getKey()));return result;}
 public static java.util.List<com.wms.dto.PermissionGroupDTO> groups(){return ALL.entrySet().stream().filter(e->e.getKey().startsWith("page:")).map(page->new com.wms.dto.PermissionGroupDTO(page.getKey(),page.getValue().substring(3),PARENTS.entrySet().stream().filter(x->x.getValue().equals(page.getKey())).map(x->new com.wms.dto.PermissionGroupDTO.PermissionItemDTO(x.getKey(),ALL.get(x.getKey()).substring(3))).toList())).toList();}
}
