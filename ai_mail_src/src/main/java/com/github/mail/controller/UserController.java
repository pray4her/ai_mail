package com.github.mail.controller;

import com.github.mail.repo.User.domain.Users;
import com.github.mail.service.User.impl.UserServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户登录接口
 * @author Aster
 * @date 2026/1/9
 */


@RestController
@RequestMapping("/api/home")
public class UserController {

    @Resource
    private UserServiceImpl userService;

    //用户登录
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Users user) {

        String loginTips = userService.verifyUser(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登陆成功");
        response.put("token", loginTips);

        if (!"用户名或密码错误".equals(loginTips)) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", loginTips));
        }
    }
}
