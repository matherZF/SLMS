package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.UserCreateReq;
import com.wms.dto.UserDTO;
import com.wms.dto.UserUpdateReq;
import com.wms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理。
 *
 * <p>类级 {@code @PreAuthorize} 与 {@code SecurityConfig} 里的 {@code /api/users/** → system:user}
 * 是同一道权限的两层实现：路径规则防的是"没登录就调"，注解防的是"将来路径调整后保护静默失效"。
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('system:user')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<Page<UserDTO>> page(@RequestParam(required = false) String username,
                                     @RequestParam(required = false) String displayName,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.page(username, displayName, page, size));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    public Result<UserDTO> create(@Valid @RequestBody UserCreateReq req) {
        return Result.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    public Result<UserDTO> update(@PathVariable Long id, @RequestBody UserUpdateReq req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = auth == null ? null : (String) auth.getPrincipal();
        return Result.ok(userService.update(id, req, currentUsername));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = auth == null ? null : (String) auth.getPrincipal();
        userService.delete(id, currentUsername);
        return Result.ok();
    }
}
