package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.UserCreateReq;
import com.wms.dto.UserDTO;
import com.wms.dto.UserUpdateReq;
import com.wms.entity.Role;
import com.wms.entity.User;
import com.wms.repository.RoleRepository;
import com.wms.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    /**
     * "能管理用户和角色"的两个权限码。
     * 判定"管理员账号"用的是这两项，而不是角色编码 == ADMIN ——
     * 这样即使有人另建一个等权角色，护栏同样生效。
     */
    private static final String PERM_USER = "system:user";
    private static final String PERM_ROLE = "system:role";

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final OperationAuditService auditService;
    private final RoleRepository roleRepo;

    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder, OperationAuditService auditService, RoleRepository roleRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.roleRepo = roleRepo;
    }

    public Page<UserDTO> page(String username, String displayName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        boolean hasU = StringUtils.hasText(username);
        boolean hasD = StringUtils.hasText(displayName);
        // 原来两个条件一起拼进 OR 查询，只填一个时另一个是空串 → LIKE '%%' 命中全部，
        // 表现就是"按用户名搜索"把所有人都捞出来了。这里按实际填写的条件分开查。
        if (hasU && hasD) {
            return userRepo.findByUsernameContainingOrDisplayNameContaining(username, displayName, pageable).map(this::toDTO);
        }
        if (hasU) return userRepo.findByUsernameContaining(username, pageable).map(this::toDTO);
        if (hasD) return userRepo.findByDisplayNameContaining(displayName, pageable).map(this::toDTO);
        return userRepo.findAll(pageable).map(this::toDTO);
    }

    public UserDTO getById(Long id) {
        return userRepo.findById(id).map(this::toDTO)
                .orElseThrow(() -> new BizException("用户不存在"));
    }

    @Transactional
    public UserDTO create(UserCreateReq req) {
        if (userRepo.existsByUsername(req.username())) {
            throw new BizException("用户名已存在");
        }
        Role role = requireRole(req.role());
        User user = new User(
                req.username(),
                passwordEncoder.encode(req.password()),
                req.displayName(),
                role.getCode()
        );
        userRepo.save(user);
        auditService.record("USER", user.getId(), user.getUsername(), "CREATE", null, null,
                "新增用户；角色：" + user.getRole() + "；姓名：" + user.getDisplayName());
        return toDTO(user);
    }

    /**
     * 修改用户。
     *
     * <p>带 {@code currentOperator} 是为了拦住"把自己锁在门外"的操作：
     * 系统里只有一个管理员时，把自己降级或停用之后再没人能改回来，只能改库。
     * 同一条不变式在 {@link #delete} 里也会再校验一次。
     */
    @Transactional
    public UserDTO update(Long id, UserUpdateReq req, String currentOperator) {
        User user = userRepo.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        String before = user.getDisplayName() + "/" + user.getRole() + "/" + user.isEnabled();

        String newRole = StringUtils.hasText(req.role()) ? requireRole(req.role()).getCode() : user.getRole();
        boolean newEnabled = req.enabled() != null ? req.enabled() : user.isEnabled();
        boolean self = user.getUsername().equalsIgnoreCase(String.valueOf(currentOperator));

        if (self && !newEnabled) {
            throw new BizException("不能停用当前登录用户");
        }
        if (self && !newRole.equals(user.getRole()) && !roleHasAdminRights(newRole)) {
            throw new BizException("不能把当前登录用户改成无用户/角色管理权限的角色，请让另一位管理员操作");
        }
        assertAtLeastOneAdmin(user, newRole, newEnabled, false);

        if (StringUtils.hasText(req.displayName())) {
            user.setDisplayName(req.displayName().trim());
        }
        user.setRole(newRole);
        user.setEnabled(newEnabled);
        if (StringUtils.hasText(req.password())) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        userRepo.save(user);
        auditService.record("USER", user.getId(), user.getUsername(), "UPDATE", null, null,
                "修改前：" + before + "；修改后：" + user.getDisplayName() + "/" + user.getRole() + "/" + user.isEnabled() +
                        (StringUtils.hasText(req.password()) ? "；同时修改密码" : ""));
        return toDTO(user);
    }

    @Transactional
    public void delete(Long id, String currentUsername) {
        User user = userRepo.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        if (user.getUsername().equals(currentUsername)) {
            throw new BizException("不能删除当前登录用户");
        }
        assertAtLeastOneAdmin(user, user.getRole(), user.isEnabled(), true);
        userRepo.deleteById(id);
        auditService.record("USER", id, user.getUsername(), "DELETE", null, null, "删除用户：" + user.getDisplayName());
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepo.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        auditService.record("USER", id, user.getUsername(), "RESET_PASSWORD", null, null, "管理员重置用户密码");
    }

    public User findByUsername(String username) {
        return userRepo.findByUsername(username).orElse(null);
    }

    /**
     * 校验"本次改动之后系统里至少还有一个可用的管理员"。
     *
     * <p>管理员 = 账号启用 + 所在角色启用且同时持有 {@code system:user} 与 {@code system:role}。
     * 把待改动的这条按改动后的状态代入重算，结果为 0 就拒绝。
     *
     * @param deleting true 表示该用户将被删除，统计时直接排除
     */
    private void assertAtLeastOneAdmin(User target, String effectiveRole, boolean effectiveEnabled, boolean deleting) {
        List<User> all = userRepo.findAll();
        Map<String, Boolean> cache = new HashMap<>();
        boolean found = false;
        for (User u : all) {
            boolean isTarget = u.getId() != null && u.getId().equals(target.getId());
            if (isTarget && deleting) continue;
            String roleCode = isTarget ? effectiveRole : u.getRole();
            boolean enabled = isTarget ? effectiveEnabled : u.isEnabled();
            if (!enabled) continue;
            if (cache.computeIfAbsent(roleCode == null ? "" : roleCode, this::roleHasAdminRights)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new BizException("系统至少需要保留一个启用的管理员（角色需同时具备用户管理与角色管理权限）");
        }
    }

    /** 角色是否"启用且同时持有 system:user 与 system:role" */
    private boolean roleHasAdminRights(String roleCode) {
        Role role = roleRepo.findByCode(roleCode).orElse(null);
        return role != null && role.isEnabled()
                && role.getPermissions().contains(PERM_USER)
                && role.getPermissions().contains(PERM_ROLE);
    }

    private UserDTO toDTO(User u) {
        Role role = roleRepo.findByCode(u.getRole()).orElse(null);
        return new UserDTO(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getRole(), role == null ? u.getRole() : role.getName(), role == null ? java.util.Set.of() : role.getPermissions(),
                u.isEnabled(),
                u.getCreatedAt()
        );
    }

    private Role requireRole(String code) {
        Role r = roleRepo.findByCode(code).orElseThrow(() -> new BizException("角色无效"));
        if (!r.isEnabled()) throw new BizException("角色已停用");
        return r;
    }
}
