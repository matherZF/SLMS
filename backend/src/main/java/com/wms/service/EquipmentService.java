package com.wms.service;
import com.wms.common.BizException; import com.wms.entity.*; import com.wms.repository.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service public class EquipmentService {
 private final EquipmentRepository equipmentRepo; private final EquipmentPartRepository partRepo; private final ProductRepository productRepo; private final EquipmentExtRefRepository extRefRepo; private final OperationAuditService auditService;
 public EquipmentService(EquipmentRepository a,EquipmentPartRepository b,ProductRepository d,EquipmentExtRefRepository x,OperationAuditService f){equipmentRepo=a;partRepo=b;productRepo=d;extRefRepo=x;auditService=f;}
 public List<Map<String,Object>> list(String keyword){String k=keyword==null?"":keyword.trim(); return equipmentRepo.findByCodeContainingOrNameContaining(k,k).stream().map(this::equipmentMap).toList();}
 @Transactional public Map<String,Object> save(Long id,Map<String,Object> req){boolean creating=id==null;Equipment e=creating?new Equipment():equipmentRepo.findById(id).orElseThrow(()->new BizException("设备不存在"));String oldStatus=e.getStatus();String code=str(req,"code");if(code==null)throw new BizException("设备编码不能为空");if(creating&&equipmentRepo.existsByCode(code))throw new BizException("设备编码已存在");e.setCode(code);e.setName(required(req,"name","设备名称不能为空"));e.setModel(str(req,"model"));e.setLocation(str(req,"location"));e.setZone(str(req,"zone"));e.setStatus(Optional.ofNullable(str(req,"status")).orElse("RUNNING"));e.setManufacturer(str(req,"manufacturer"));e.setRemark(str(req,"remark"));equipmentRepo.save(e);String extDesc=applyExtRefs(e,req);auditService.record("EQUIPMENT",e.getId(),e.getCode(),creating?"CREATE":"UPDATE",oldStatus,e.getStatus(),(creating?"新增设备：":"修改设备：")+e.getName()+(extDesc.isEmpty()?"":"；外部编码："+extDesc));return equipmentMap(e);}
 /**
  * 维护设备的取数规则（映射行）。请求里带 extRefs（[{sourceSys,extCode,takeHours,takeTasks,takeQty}]）
  * 才处理——不传 = 不动现有配置；传空数组 = 清空（设备将不再取到任何运行数据）。
  * 整表替换语义：先校验重复再删旧插新（批量删除立即下发 SQL，避免先 INSERT 后 DELETE 撞唯一键）。
  * 每行至少要勾一个指标；旧版前端不带指标字段时按三个全取处理。
  * @return 审计用的规则描述，如 "WCS/STA002(时长、任务数)"；未处理返回空串
  */
 @SuppressWarnings("unchecked")
 private String applyExtRefs(Equipment e,Map<String,Object> req){
  Object v=req.get("extRefs"); if(v==null) return "";
  List<Map<String,Object>> refs; try{refs=(List<Map<String,Object>>)v;}catch(ClassCastException ex){throw new BizException("extRefs 格式不正确");}
  Set<String> seen=new HashSet<>(); List<EquipmentExtRef> fresh=new ArrayList<>(); List<String> desc=new ArrayList<>();
  for(Map<String,Object> r:refs){
   if(r==null) continue;
   String code=str(r,"extCode"); if(code==null||code.isEmpty()) continue;
   String sysRaw=str(r,"sourceSys"); final String sys=(sysRaw==null||sysRaw.isEmpty())?"WCS":sysRaw.toUpperCase();
   if(!seen.add(sys+":"+code.toUpperCase())) throw new BizException("外部编码重复："+sys+"/"+code);
   boolean hours=flag(r,"takeHours"), tasks=flag(r,"takeTasks"), qty=flag(r,"takeQty");
   if(!hours&&!tasks&&!qty) throw new BizException("映射 "+sys+"/"+code.toUpperCase()+" 至少要勾选一个指标（时长/任务数/件数）");
   extRefRepo.findBySourceSysIgnoreCaseAndExtCodeIgnoreCase(sys,code).ifPresent(other->{
    if(!Objects.equals(other.getEquipment().getId(),e.getId()))
     throw new BizException("外部编码 "+sys+"/"+code+" 已挂在设备 "+other.getEquipment().getCode()+" 上");
   });
   EquipmentExtRef x=new EquipmentExtRef(); x.setEquipment(e); x.setSourceSys(sys); x.setExtCode(code.toUpperCase());
   x.setTakeHours(hours); x.setTakeTasks(tasks); x.setTakeQty(qty);
   fresh.add(x);
   List<String> picked=new ArrayList<>(); if(hours)picked.add("时长"); if(tasks)picked.add("任务数"); if(qty)picked.add("件数");
   desc.add(sys+"/"+code.toUpperCase()+"("+String.join("、",picked)+")");
  }
  extRefRepo.deleteByEquipmentIdBulk(e.getId());
  extRefRepo.saveAll(fresh);
  return String.join("、",desc);
 }
 /** 布尔开关：字段缺省（旧客户端/未传）视为 true，只有明确 false 才不取 */
 private boolean flag(Map<String,Object> m,String k){Object v=m.get(k);return v==null||Boolean.parseBoolean(String.valueOf(v));}
 @Transactional public Map<String,Object> bindPart(Long equipmentId,Map<String,Object> req){Long productId=num(req,"productId");Equipment e=equipmentRepo.findById(equipmentId).orElseThrow(()->new BizException("设备不存在"));Product p=productRepo.findById(productId).orElseThrow(()->new BizException("备件不存在"));EquipmentPart x=partRepo.findByEquipmentIdAndProductId(equipmentId,productId).orElseGet(EquipmentPart::new);boolean creating=x.getId()==null;x.setEquipment(e);x.setProduct(p);x.setInstalledQty(Optional.ofNullable(integer(req,"installedQty")).orElse(1));x.setChangeCycleDays(integer(req,"changeCycleDays"));partRepo.save(x);auditService.record("EQUIPMENT",e.getId(),e.getCode(),creating?"BIND_PART":"UPDATE_PART",null,null,"关联备件 "+p.getSku()+"；安装数量："+x.getInstalledQty());return partMap(x);}
 public List<Map<String,Object>> parts(Long id){return partRepo.findByEquipmentId(id).stream().map(this::partMap).toList();}
 /** 设备编辑弹框里的关联备件分页（设备多备件时不用一次拉全量） */
 public org.springframework.data.domain.Page<Map<String,Object>> partsPage(Long id, org.springframework.data.domain.Pageable pageable){return partRepo.findByEquipmentId(id,pageable).map(this::partMap);}
 /** 某备件装在哪些设备上——出库单据此给出领用设备候选；通用件通常有多台 */
 public List<Map<String,Object>> devicesOfProduct(Long productId){
  Map<Long,Map<String,Object>> out=new LinkedHashMap<>();
  for(EquipmentPart x:partRepo.findByProductId(productId)){
   Equipment e=x.getEquipment(); if(e==null) continue;
   Map<String,Object> m=new LinkedHashMap<>();
   m.put("equipmentId",e.getId()); m.put("equipmentCode",e.getCode()); m.put("equipmentName",e.getName());
   m.put("installedQty",x.getInstalledQty()); m.put("changeCycleDays",x.getChangeCycleDays());
   out.putIfAbsent(e.getId(),m);
  }
  return new ArrayList<>(out.values());
 }
 /** 修改已关联备件的参数；备件本身不可换（换备件=解绑后重新关联），避免撞 equipment_id+product_id 唯一键 */
 @Transactional public Map<String,Object> updatePart(Long equipmentId,Long partId,Map<String,Object> req){EquipmentPart x=requirePart(equipmentId,partId);
  // 只覆盖请求里出现的字段：避免前端只传一个字段时把其余字段清空
  if(req.containsKey("installedQty"))x.setInstalledQty(Optional.ofNullable(integer(req,"installedQty")).orElse(x.getInstalledQty()));
  if(req.containsKey("changeCycleDays"))x.setChangeCycleDays(integer(req,"changeCycleDays"));
  partRepo.save(x);auditService.record("EQUIPMENT",equipmentId,x.getEquipment().getCode(),"UPDATE_PART",null,null,"修改备件 "+x.getProduct().getSku()+"；单机数量："+x.getInstalledQty());return partMap(x);}
 /** 解除设备与备件的关联；备件寿命/领用历史由 wms_spare_part_usage 承载，这里只解绑档案关系 */
 @Transactional public void unbindPart(Long equipmentId,Long partId){EquipmentPart x=requirePart(equipmentId,partId);String sku=x.getProduct().getSku();String code=x.getEquipment().getCode();partRepo.delete(x);auditService.record("EQUIPMENT",equipmentId,code,"UNBIND_PART",null,null,"解除备件关联 "+sku);}
 private EquipmentPart requirePart(Long equipmentId,Long partId){EquipmentPart x=partRepo.findById(partId).orElseThrow(()->new BizException("关联记录不存在"));if(!Objects.equals(x.getEquipment().getId(),equipmentId))throw new BizException("该备件不属于这台设备");return x;}
 /**
  * 补货点 = 备件档案的安全库存 + 本设备补货周期内的预计消耗量（更换次数 × 单机数量）。
  *
  * <p>安全库存与采购周期取自备件档案：同一个 SKU 只有一份共享库存，也只能有一个到货天数。
  * 设备侧只贡献「这台设备多久换一次」和「装了几个」。设备级安全库存会按设备重复计数——
  * 一个轴承装在 3 台设备上各填 10 件，仓库究竟该备 10 件还是 30 件，没有答案。</p>
  *
  * <p>更换周期或采购周期没填（或填 0）时不加预估消耗量，只按安全库存算。</p>
  */
 static int reorderPoint(EquipmentPart p, Product product){int safety=Math.max(product.getSafetyStock(),0); Integer cycle=p.getChangeCycleDays(); int lead=product.getProcurementLeadDays(); if(cycle==null||cycle<=0||lead<=0)return safety; int times=Math.max(1,(int)Math.ceil((double)lead/cycle)); return safety+times*p.getInstalledQty();}
 private Map<String,Object> equipmentMap(Equipment e){Map<String,Object>m=new LinkedHashMap<>();m.put("id",e.getId());m.put("code",e.getCode());m.put("name",e.getName());m.put("model",e.getModel());m.put("location",e.getLocation());m.put("zone",e.getZone());m.put("status",e.getStatus());m.put("manufacturer",e.getManufacturer());m.put("remark",e.getRemark());List<Map<String,Object>> refs=new ArrayList<>();for(EquipmentExtRef r:extRefRepo.findByEquipmentId(e.getId())){Map<String,Object>x=new LinkedHashMap<>();x.put("id",r.getId());x.put("sourceSys",r.getSourceSys());x.put("extCode",r.getExtCode());x.put("takeHours",r.isTakeHours());x.put("takeTasks",r.isTakeTasks());x.put("takeQty",r.isTakeQty());refs.add(x);}m.put("extRefs",refs);return m;}
 private Map<String,Object> partMap(EquipmentPart p){Map<String,Object>m=new LinkedHashMap<>();m.put("id",p.getId());m.put("equipmentId",p.getEquipment().getId());m.put("equipmentCode",p.getEquipment().getCode());m.put("equipmentName",p.getEquipment().getName());m.put("productId",p.getProduct().getId());m.put("sku",p.getProduct().getSku());m.put("productName",p.getProduct().getName());m.put("installedQty",p.getInstalledQty());m.put("changeCycleDays",p.getChangeCycleDays());return m;}
 private String str(Map<String,Object>m,String k){Object v=m.get(k);return v==null?null:String.valueOf(v).trim();} private String required(Map<String,Object>m,String k,String msg){String v=str(m,k);if(v==null||v.isEmpty())throw new BizException(msg);return v;} private Long num(Map<String,Object>m,String k){Object v=m.get(k);if(v==null||String.valueOf(v).isBlank())return null;return Long.valueOf(String.valueOf(v));} private Integer integer(Map<String,Object>m,String k){Long v=num(m,k);return v==null?null:v.intValue();}
}
