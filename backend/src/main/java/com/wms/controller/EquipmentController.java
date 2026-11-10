package com.wms.controller;
import com.wms.common.BizException; import com.wms.common.Result; import com.wms.dto.DeviceStatDailyVO; import com.wms.entity.Equipment; import com.wms.repository.EquipmentRepository; import com.wms.service.DeviceCodeResolver; import com.wms.service.DeviceStatDailyQuery; import com.wms.service.EquipmentService; import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
@RestController @RequestMapping("/api/equipments") public class EquipmentController { private final EquipmentService s; private final EquipmentRepository equipmentRepo; private final DeviceStatDailyQuery statQuery; private final DeviceCodeResolver codeResolver;
 public EquipmentController(EquipmentService s, EquipmentRepository equipmentRepo, DeviceStatDailyQuery statQuery, DeviceCodeResolver codeResolver){this.s=s;this.equipmentRepo=equipmentRepo;this.statQuery=statQuery;this.codeResolver=codeResolver;}
 @GetMapping public Result<List<Map<String,Object>>> list(@RequestParam(required=false)String keyword){return Result.ok(s.list(keyword));}
 @PostMapping @PreAuthorize("hasAuthority('master:edit')") public Result<Map<String,Object>> create(@RequestBody Map<String,Object> r){return Result.ok(s.save(null,r));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('master:edit')") public Result<Map<String,Object>> update(@PathVariable Long id,@RequestBody Map<String,Object>r){return Result.ok(s.save(id,r));}
 @GetMapping("/{id}/parts") public Result<List<Map<String,Object>>> parts(@PathVariable Long id){return Result.ok(s.parts(id));}
 /** 设备编辑弹框里的关联备件分页列表 */
 @GetMapping("/{id}/parts/page") public Result<Page<Map<String,Object>>> partsPage(@PathVariable Long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return Result.ok(s.partsPage(id,org.springframework.data.domain.PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),100))));}
 /**
  * 设备运行数据：读本地 wms_device_stat_daily（数字孪生 / WMS 每日同步落表），按设备编码匹配。
  * 不依赖外部系统是否在线，源库没数据时返回空列表 + 汇总为 0。
  */
 @GetMapping("/{id}/stats")
 public Result<Map<String,Object>> stats(@PathVariable Long id,@RequestParam(required=false) Integer days){
  Equipment e=equipmentRepo.findById(id).orElseThrow(()->new BizException("设备不存在"));
  int n=(days==null||days<=0)?30:Math.min(days,366);
  LocalDate to=LocalDate.now(); LocalDate from=to.minusDays(n-1L);
  // 取数完全按设备配置的映射规则：源 + 外部编码 + 勾选的指标；没配映射就没有数据
  List<com.wms.service.DeviceCodeResolver.MappingRule> rules=codeResolver.rules(e.getCode());
  List<DeviceStatDailyVO> rows=new ArrayList<>(statQuery.listMergedByRules(rules,from,to));
  BigDecimal hours=BigDecimal.ZERO; long tasks=0L, qty=0L;
  for(DeviceStatDailyVO r:rows){
   if(r.runningHours()!=null) hours=hours.add(r.runningHours());
   if(r.taskCount()!=null) tasks+=r.taskCount();
   if(r.totalQty()!=null) qty+=r.totalQty();
  }
  Map<String,Object> totals=new LinkedHashMap<>(); totals.put("hours",hours); totals.put("tasks",tasks); totals.put("qty",qty);
  Map<String,Object> out=new LinkedHashMap<>();
  out.put("deviceId",e.getId()); out.put("code",e.getCode()); out.put("name",e.getName()); out.put("model",e.getModel());
  out.put("location",e.getLocation()); out.put("zone",e.getZone()); out.put("status",e.getStatus()); out.put("manufacturer",e.getManufacturer());
  out.put("extRules",rules); out.put("days",n); out.put("from",from); out.put("to",to); out.put("totals",totals); out.put("rows",rows);
  return Result.ok(out);
 }
 /** 某备件装在哪些设备上，供出库单选择领用设备 */
 @GetMapping("/by-product") public Result<List<Map<String,Object>>> devicesOfProduct(@RequestParam Long productId){return Result.ok(s.devicesOfProduct(productId));}
 @PostMapping("/{id}/parts") @PreAuthorize("hasAuthority('master:edit')") public Result<Map<String,Object>> bind(@PathVariable Long id,@RequestBody Map<String,Object>r){return Result.ok(s.bindPart(id,r));}
 @PutMapping("/{id}/parts/{partId}") @PreAuthorize("hasAuthority('master:edit')") public Result<Map<String,Object>> updatePart(@PathVariable Long id,@PathVariable Long partId,@RequestBody Map<String,Object> r){return Result.ok(s.updatePart(id,partId,r));}
 @DeleteMapping("/{id}/parts/{partId}") @PreAuthorize("hasAuthority('master:edit')") public Result<Object> unbind(@PathVariable Long id,@PathVariable Long partId){s.unbindPart(id,partId);return Result.ok(null);}
}
