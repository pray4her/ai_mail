package com.github.mail.controller;

import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.dto.ChangePasswordRequest;
import com.github.mail.repo.User.dto.LoginRequest;
import com.github.mail.repo.User.dto.RegisterRequest;
import com.github.mail.repo.User.dto.UserInfoDTO;
import com.github.mail.service.User.IAccountAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final IAccountAuthService accountAuthService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            Users created = accountAuthService.register(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "注册成功",
                    "user", toUserInfo(created)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            String token = accountAuthService.login(request.getUsername(), request.getPassword());
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "登陆成功");
            resp.put("token", token);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("auth.username");
            Users me = accountAuthService.getCurrentUser(username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user", toUserInfo(me)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(HttpServletRequest request, @RequestBody ChangePasswordRequest body) {
        try {
            String username = (String) request.getAttribute("auth.username");
            accountAuthService.changePassword(username, body.getOldPassword(), body.getNewPassword());
            return ResponseEntity.ok(Map.of("success", true, "message", "修改成功"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        try {
            String jti = (String) request.getAttribute("auth.jti");
            LocalDateTime expiresAt = (LocalDateTime) request.getAttribute("auth.expiresAt");
            accountAuthService.logout(jti, expiresAt);
            return ResponseEntity.ok(Map.of("success", true, "message", "已登出"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
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
