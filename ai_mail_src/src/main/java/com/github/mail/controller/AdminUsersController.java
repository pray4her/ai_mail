package com.github.mail.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.dto.AdminCreateUserRequest;
import com.github.mail.repo.User.dto.AdminResetPasswordRequest;
import com.github.mail.repo.User.dto.AdminUpdateStatusRequest;
import com.github.mail.repo.User.dto.UserInfoDTO;
import com.github.mail.service.User.IAccountAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final IAccountAdminService accountAdminService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权限"));
        }
        Page<Users> usersPage = accountAdminService.listUsers(page, size);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", usersPage.getTotal(),
                "page", usersPage.getCurrent(),
                "size", usersPage.getSize(),
                "records", usersPage.getRecords().stream().map(this::toUserInfo).collect(Collectors.toList())
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request, @RequestBody AdminCreateUserRequest body) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权限"));
        }
        try {
            Users created = accountAdminService.createUser(body.getUsername(), body.getPassword(), body.getRole());
            return ResponseEntity.ok(Map.of("success", true, "user", toUserInfo(created)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(HttpServletRequest request, @PathVariable("id") long id) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权限"));
        }
        try {
            Users user = accountAdminService.getUser(id);
            return ResponseEntity.ok(Map.of("success", true, "user", toUserInfo(user)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(HttpServletRequest request,
                                                            @PathVariable("id") long id,
                                                            @RequestBody AdminUpdateStatusRequest body) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权限"));
        }
        try {
            accountAdminService.updateStatus(id, body.getStatus());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(HttpServletRequest request,
                                                             @PathVariable("id") long id,
                                                             @RequestBody AdminResetPasswordRequest body) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权限"));
        }
        try {
            accountAdminService.resetPassword(id, body.getPassword());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("auth.role");
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }

    private UserInfoDTO toUserInfo(Users u) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUser());
        dto.setRole(u.getRole());
        dto.setStatus(u.getStatus());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setUpdatedAt(u.getUpdatedAt());
        return dto;
    }
}
