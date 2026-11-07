package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.UserOptionVO;
import com.wms.entity.User;
import com.wms.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户下拉选项：任意已登录用户可访问。
 *
 * <p>与 {@link UserController} 分开的原因：那边整类挂了 {@code system:user} 权限（用户管理），
 * 而领用单的「实际领用人」是所有领用人都要用的下拉，不能要求管理员权限。
 * 本接口只读、只返回启用用户的显示名/用户名，不涉及角色和密码。
 */
@RestController
@RequestMapping("/api/user-options")
public class UserOptionController {

    private final UserRepository userRepository;

    public UserOptionController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public Result<List<UserOptionVO>> list() {
        List<UserOptionVO> options = userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .filter(User::isEnabled)
                .map(u -> new UserOptionVO(u.getUsername(),
                        u.getDisplayName() == null || u.getDisplayName().isBlank()
                                ? u.getUsername() : u.getDisplayName()))
                .toList();
        return Result.ok(options);
    }
}
