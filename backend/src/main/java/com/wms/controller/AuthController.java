package com.wms.controller;

import com.wms.common.BizException;
import com.wms.common.Result;
import com.wms.config.JwtUtil;
import com.wms.dto.LoginReq;
import com.wms.dto.LoginRes;
import com.wms.dto.PasswordChangeReq;
import com.wms.dto.ProfileUpdateReq;
import com.wms.dto.UserDTO;
import com.wms.entity.User;
import com.wms.repository.UserRepository;
import com.wms.repository.RoleRepository;
import com.wms.entity.Role;
import com.wms.service.UserService;
import com.wms.service.LoginThrottleService;
import com.wms.service.OperationAuditService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepository;
    private final LoginThrottleService loginThrottle;
    private final OperationAuditService auditService;

    /**
     * 用户不存在时也跑一次 BCrypt，避免通过响应耗时判断用户名是否存在（时序侧信道）。
     * 这个值本身不对应任何真实账号。
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    public AuthController(UserRepository userRepository,
                          UserService userService,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil, RoleRepository roleRepository,
                          LoginThrottleService loginThrottle,
                          OperationAuditService auditService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.roleRepository = roleRepository;
        this.loginThrottle = loginThrottle;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public Result<LoginRes> login(@Valid @RequestBody LoginReq req) {
        long locked = loginThrottle.lockedSeconds(req.username());
        if (locked > 0) {
            long minutes = (locked + 59) / 60;
            auditService.record("AUTH", null, req.username(), "LOGIN_FAIL", null, null, null,
                    "登录失败：密码错误次数过多，账号临时锁定");
            return Result.fail("密码错误次数过多，请 " + minutes + " 分钟后再试");
        }
        User user = userRepository.findByUsername(req.username()).orElse(null);
        boolean matched = passwordEncoder.matches(req.password(),
                user == null ? DUMMY_HASH : user.getPassword());
        if (user == null || !matched) {
            loginThrottle.recordFailure(req.username());
            auditService.record("AUTH", null, req.username(), "LOGIN_FAIL", null, null, null,
                    "登录失败：用户名或密码错误");
            return Result.fail("用户名或密码错误");
        }
        if (!user.isEnabled()) {
            auditService.record("AUTH", user.getId(), user.getUsername(), "LOGIN_FAIL", null, null, null,
                    "登录失败：账号已禁用");
            return Result.fail("账号已禁用");
        }
        Role role=roleRepository.findByCode(user.getRole()).orElseThrow(()->new BizException("用户角色不存在"));
        if(!role.isEnabled()) {
            auditService.record("AUTH", user.getId(), user.getUsername(), "LOGIN_FAIL", null, null, null,
                    "登录失败：用户角色已停用");
            return Result.fail("用户角色已停用");
        }
        loginThrottle.clear(req.username());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginRes res = new LoginRes(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),role.getName(),role.getPermissions());
        auditService.record("AUTH", user.getId(), user.getUsername(), "LOGIN", null, null, user.getDisplayName(),
                "登录成功，角色: " + role.getName());
        return Result.ok(res);
    }

    @GetMapping("/me")
    public Result<UserDTO> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BizException("未登录");
        }
        String username = (String) auth.getPrincipal();
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        Role role=roleRepository.findByCode(user.getRole()).orElseThrow(()->new BizException("用户角色不存在"));
        UserDTO dto = new UserDTO(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(),role.getName(),role.getPermissions(), user.isEnabled(), user.getCreatedAt());
        return Result.ok(dto);
    }

    /** 当前登录用户修改个人信息（姓名） */
    @PutMapping("/profile")
    public Result<UserDTO> updateProfile(@RequestBody ProfileUpdateReq req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BizException("未登录");
        }
        User user = userService.findByUsername((String) auth.getPrincipal());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (req.displayName() != null) {
            String name = req.displayName().trim();
            if (name.length() > 64) {
                throw new BizException("姓名不能超过 64 个字符");
            }
            user.setDisplayName(name.isEmpty() ? null : name);
        }
        userRepository.save(user);
        Role role = roleRepository.findByCode(user.getRole()).orElseThrow(() -> new BizException("用户角色不存在"));
        UserDTO dto = new UserDTO(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), role.getName(), role.getPermissions(), user.isEnabled(), user.getCreatedAt());
        return Result.ok(dto);
    }

    /** 当前登录用户修改密码（需验证原密码） */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordChangeReq req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BizException("未登录");
        }
        User user = userService.findByUsername((String) auth.getPrincipal());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (req.oldPassword() == null || !passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new BizException("新密码长度至少 6 位");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        auditService.record("AUTH", user.getId(), user.getUsername(), "CHANGE_PASSWORD", null, null, user.getDisplayName(),
                "用户自主修改密码");
        return Result.ok();
    }
}
