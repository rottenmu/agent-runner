package com.zimo.module.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.BizException;
import com.zimo.module.auth.dto.LoginDTO;
import com.zimo.module.auth.dto.RegisterDTO;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.AuthSessionService;
import com.zimo.module.auth.support.CurrentLoginUser;
import com.zimo.module.auth.support.CurrentLoginUserUtils;
import com.zimo.module.auth.support.DepartmentOptions;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final AuthSessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserMapper userMapper, AuthSessionService sessionService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        validateLogin(dto);
        if (!DepartmentOptions.isAllowed(dto.getDepartment())) {
            throw new BizException(401, "账户、密码或部门错误");
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException(401, "账户、密码或部门错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        if (!dto.getDepartment().trim().equals(user.getDepartment())) {
            throw new BizException(401, "账户、密码或部门错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "账户、密码或部门错误");
        }
        sessionService.login(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", sessionService.getTokenValue());
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("department", user.getDepartment());
        data.put("permissions", sessionService.getPermissions());
        return ApiResponse.ok(data);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        validateRegister(dto);
        String username = dto.getUsername().trim();
        String nickname = dto.getNickname().trim();
        String department = dto.getDepartment().trim();
        if (!DepartmentOptions.isAllowed(department)) {
            throw new BizException(400, "部门不在可选范围内");
        }
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (existing != null) {
            throw new BizException(409, "账户已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setDepartment(department);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("department", user.getDepartment());
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        sessionService.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        CurrentLoginUser user = CurrentLoginUserUtils.currentUser(sessionService, userMapper);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.userId());
        data.put("username", user.username());
        data.put("nickname", user.nickname());
        data.put("department", user.department());
        data.put("avatar", user.avatar());
        data.put("permissions", sessionService.getPermissions());
        return ApiResponse.ok(data);
    }

    private void validateLogin(LoginDTO dto) {
        requireText(dto == null ? null : dto.getUsername(), "用户名不能为空");
        requireText(dto.getPassword(), "密码不能为空");
        requireText(dto.getDepartment(), "部门不能为空");
    }

    private void validateRegister(RegisterDTO dto) {
        requireText(dto == null ? null : dto.getNickname(), "姓名不能为空");
        requireText(dto.getUsername(), "账户不能为空");
        requireText(dto.getPassword(), "密码不能为空");
        requireText(dto.getDepartment(), "部门不能为空");
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(400, message);
        }
    }
}
