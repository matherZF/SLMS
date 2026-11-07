package com.wms.service;
import com.wms.common.BizException; import com.wms.dto.*; import com.wms.entity.Role; import com.wms.entity.User; import com.wms.repository.*;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*; import java.util.stream.Collectors;
@Service public class RoleService {
 private final RoleRepository repo; private final UserRepository users; private final OperationAuditService audit;
 public RoleService(RoleRepository r,UserRepository u,OperationAuditService a){repo=r;users=u;audit=a;}
 public List<RoleDTO> list(){return repo.findAll().stream().sorted(Comparator.comparing(Role::getId)).map(this::dto).toList();}
 public java.util.List<PermissionGroupDTO> permissions(){return PermissionCatalog.groups();}
 @Transactional public RoleDTO create(RoleSaveReq req){String code=normalize(req.code());if(repo.existsByCode(code))throw new BizException("角色编码已存在");Role r=new Role();r.setCode(code);r.setBuiltin(false);apply(r,req);repo.save(r);audit.record("ROLE",r.getId(),code,"CREATE",null,null,"新增角色："+r.getName());return dto(r);}
 @Transactional public RoleDTO update(Long id,RoleSaveReq req){Role r=repo.findById(id).orElseThrow(()->new BizException("角色不存在"));if(r.isBuiltin()&&!r.getCode().equals(normalize(req.code())))throw new BizException("内置角色编码不可修改");apply(r,req);assertAdminRoleRemains(r);repo.save(r);audit.record("ROLE",id,r.getCode(),"UPDATE",null,null,"更新角色权限："+r.getName());return dto(r);}
 @Transactional public void delete(Long id){Role r=repo.findById(id).orElseThrow(()->new BizException("角色不存在"));if(r.isBuiltin())throw new BizException("内置角色不可删除");if(users.countByRole(r.getCode())>0)throw new BizException("该角色仍有用户，不能删除");repo.delete(r);audit.record("ROLE",id,r.getCode(),"DELETE",null,null,"删除角色："+r.getName());}
 public Role requireEnabled(String code){Role r=repo.findByCode(code).orElseThrow(()->new BizException("角色不存在"));if(!r.isEnabled())throw new BizException("角色已停用");return r;}
 private void apply(Role r,RoleSaveReq q){r.setName(q.name().trim());r.setDescription(q.description());r.setEnabled(q.enabled()==null||q.enabled());r.setPermissions(PermissionCatalog.validate(q.permissions()));}

 /**
  * 管理员角色护栏。
  *
  * <p>管理员角色 = 启用 + 同时持有 system:user 与 system:role。两条规则：
  * <ol>
  *   <li>内置 ADMIN 改完之后必须仍是管理员角色 —— 否则任何人都能在界面上
  *       把 ADMIN 的启用开关一关或把权限清空，所有管理员下一次请求就变匿名，只能改库恢复；</li>
  *   <li>其它角色若被改掉这两项权限或停用，系统里必须还有另一个
  *       "启用 + 已分配给至少一个启用用户"的管理员角色顶上。</li>
  * </ol>
  * 注意 {@code RoleSaveReq.permissions} 传 null 会被当成空集合、等于清空，
  * 上面第 1 条同时也是这种情况的兜底。
  */
 private void assertAdminRoleRemains(Role r){
  boolean stillAdmin=r.isEnabled()&&r.getPermissions().contains("system:user")&&r.getPermissions().contains("system:role");
  if(stillAdmin)return;
  if(r.isBuiltin()&&"ADMIN".equals(r.getCode()))
   throw new BizException("内置 ADMIN 角色必须保持启用，并保留「用户管理」与「角色管理」权限");
  Set<String> inUse=users.findAll().stream().filter(User::isEnabled).map(User::getRole).collect(Collectors.toSet());
  boolean fallback=repo.findAll().stream().filter(x->!x.getId().equals(r.getId()))
          .anyMatch(x->x.isEnabled()&&x.getPermissions().contains("system:user")&&x.getPermissions().contains("system:role")&&inUse.contains(x.getCode()));
  if(!fallback)throw new BizException("系统至少需要保留一个启用、且已分配给用户的管理员角色");
 }

 private String normalize(String v){String s=v.trim().toUpperCase(Locale.ROOT);if(!s.matches("[A-Z][A-Z0-9_]{1,31}"))throw new BizException("角色编码须为2-32位大写字母、数字或下划线");return s;}
 private RoleDTO dto(Role r){return new RoleDTO(r.getId(),r.getCode(),r.getName(),r.getDescription(),r.isBuiltin(),r.isEnabled(),r.getPermissions(),r.getCreatedAt());}
}
