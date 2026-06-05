package com.github.mail.service.User.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.IAccountAuthService;
import com.github.mail.service.User.ITokenInvalidationService;
import com.github.mail.service.User.JwtTokenService;
import com.github.mail.utils.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountAuthServiceImpl implements IAccountAuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final AccountAuthProperties accountAuthProperties;
    private final ITokenInvalidationService tokenInvalidationService;

    @Override
    @Transactional
    public Users register(final String username, final String password) {
        validateUsernameAndPassword(username, password);

        long existingCount = userMapper.selectCount(new QueryWrapper<Users>().eq("is_deleted", 0));
        if (existingCount > 0 && !accountAuthProperties.isAllowSelfRegisterAfterInitialized()) {
            throw new IllegalArgumentException("不允许自助注册");
        }

        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("user", username).eq("is_deleted", 0);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        Users entity = new Users();
        entity.setUser(username);
        entity.setPasswordHash(passwordHasher.hash(password));
        entity.setStatus(STATUS_ACTIVE);
        entity.setRole(existingCount == 0 ? ROLE_ADMIN : ROLE_USER);
        entity.setIsDeleted(0);
        userMapper.insert(entity);
        entity.setPasswd(null);
        return entity;
    }

    @Override
    @Transactional
    public String login(final String username, final String password) {
        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("user", username).eq("is_deleted", 0);
        Users dbUser = userMapper.selectOne(wrapper);
        if (dbUser == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (STATUS_DISABLED.equalsIgnoreCase(dbUser.getStatus())) {
            throw new IllegalArgumentException("账户不可用");
        }

        boolean verified = false;
        if (dbUser.getPasswordHash() != null && !dbUser.getPasswordHash().isBlank()) {
            verified = passwordHasher.verify(password, dbUser.getPasswordHash());
        } else if (dbUser.getPasswd() != null && !dbUser.getPasswd().isBlank()) {
            verified = dbUser.getPasswd().equals(password);
            if (verified) {
                Users update = new Users();
                update.setId(dbUser.getId());
                update.setPasswordHash(passwordHasher.hash(password));
                update.setPasswd(null);
                userMapper.updateById(update);
                dbUser.setPasswordHash(update.getPasswordHash());
                dbUser.setPasswd(null);
            }
        }

        if (!verified) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String role = dbUser.getRole() == null || dbUser.getRole().isBlank() ? ROLE_USER : dbUser.getRole();
        return jwtTokenService.createToken(dbUser.getUser(), role);
    }

    @Override
    public Users getCurrentUser(final String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("user", username).eq("is_deleted", 0);
        Users dbUser = userMapper.selectOne(wrapper);
        if (dbUser == null) {
            throw new IllegalArgumentException("未登录");
        }
        dbUser.setPasswd(null);
        dbUser.setPasswordHash(null);
        return dbUser;
    }

    @Override
    @Transactional
    public void changePassword(final String username, final String oldPassword, final String newPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("未登录");
        }
        if (newPassword == null || newPassword.length() < accountAuthProperties.getMinPasswordLength()) {
            throw new IllegalArgumentException("新密码不符合要求");
        }

        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("user", username).eq("is_deleted", 0);
        Users dbUser = userMapper.selectOne(wrapper);
        if (dbUser == null) {
            throw new IllegalArgumentException("未登录");
        }
        if (STATUS_DISABLED.equalsIgnoreCase(dbUser.getStatus())) {
            throw new IllegalArgumentException("账户不可用");
        }

        boolean verified = false;
        if (dbUser.getPasswordHash() != null && !dbUser.getPasswordHash().isBlank()) {
            verified = passwordHasher.verify(oldPassword, dbUser.getPasswordHash());
        } else if (dbUser.getPasswd() != null && !dbUser.getPasswd().isBlank()) {
            verified = dbUser.getPasswd().equals(oldPassword);
        }
        if (!verified) {
            throw new IllegalArgumentException("旧密码错误");
        }

        Users update = new Users();
        update.setId(dbUser.getId());
        update.setPasswordHash(passwordHasher.hash(newPassword));
        update.setPasswd(null);
        userMapper.updateById(update);
    }

    @Override
    public void logout(final String jti, final LocalDateTime expiresAt) {
        if (!accountAuthProperties.isServerLogoutEnabled()) {
            return;
        }
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        tokenInvalidationService.invalidate(jti, expiresAt);
    }

    private void validateUsernameAndPassword(final String username, final String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < accountAuthProperties.getMinPasswordLength()) {
            throw new IllegalArgumentException("密码长度不足");
        }
    }
}
