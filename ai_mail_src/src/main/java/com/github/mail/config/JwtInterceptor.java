package com.github.mail.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.ITokenInvalidationService;
import com.github.mail.service.User.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * JWT拦截器
 * @author Aster
 * @date 2026/1/9
 */

//jwt令牌
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String STATUS_DISABLED = "DISABLED";

    private final JwtTokenService jwtTokenService;
    private final AccountAuthProperties accountAuthProperties;
    private final ITokenInvalidationService tokenInvalidationService;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(final HttpServletRequest request, @NonNull final HttpServletResponse response, @NonNull final Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid JWT token");
            return false;
        }
        String token = authHeader.substring(7);
        try {
            Claims claims = jwtTokenService.parse(token);
            //根据用户名作为claim标识
            String username = claims.getSubject();
            if (username == null || username.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("The user ID authentication is invalid");
                return false;
            }

            String jti = claims.getId();
            if (accountAuthProperties.isServerLogoutEnabled() && tokenInvalidationService.isInvalidated(jti)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid authentication");
                return false;
            }

            QueryWrapper<Users> wrapper = new QueryWrapper<>();
            wrapper.eq("user", username).eq("is_deleted", 0);
            Users dbUser = userMapper.selectOne(wrapper);
            if (dbUser == null || STATUS_DISABLED.equalsIgnoreCase(dbUser.getStatus())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid authentication");
                return false;
            }

            request.setAttribute("auth.username", username);
            request.setAttribute("auth.role", dbUser.getRole());
            request.setAttribute("auth.jti", jti);
            if (claims.getExpiration() != null) {
                LocalDateTime expiresAt = claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                request.setAttribute("auth.expiresAt", expiresAt);
            }
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid authentication");
            return false;
        }
    }
}

