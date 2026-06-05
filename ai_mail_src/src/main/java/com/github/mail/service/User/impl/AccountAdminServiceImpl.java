package com.github.mail.service.User.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.mail.model.config.Properties.AccountAuthProperties;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.IAccountAdminService;
import com.github.mail.utils.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements IAccountAdminService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final AccountAuthProperties accountAuthProperties;

    @Override
    public Page<Users> listUsers(final long page, final long size) {
        Page<Users> p = new Page<>(page, size);
        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0).orderByDesc("id");
        Page<Users> result = userMapper.selectPage(p, wrapper);
        result.getRecords().forEach(u -> {
            u.setPasswd(null);
            u.setPasswordHash(null);
        });
        return result;
    }

    @Override
    @Transactional
    public Users createUser(final String username, final String password, final String role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < accountAuthProperties.getMinPasswordLength()) {
            throw new IllegalArgumentException("密码长度不足");
        }

        QueryWrapper<Users> wrapper = new QueryWrapper<>();
        wrapper.eq("user", username).eq("is_deleted", 0);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String finalRole = ROLE_USER;
        if (role != null && !role.isBlank()) {
            if (ROLE_ADMIN.equalsIgnoreCase(role)) {
                finalRole = ROLE_ADMIN;
            } else if (ROLE_USER.equalsIgnoreCase(role)) {
                finalRole = ROLE_USER;
            } else {
                throw new IllegalArgumentException("角色不合法");
            }
        }

        Users entity = new Users();
        entity.setUser(username);
        entity.setPasswordHash(passwordHasher.hash(password));
        entity.setStatus(STATUS_ACTIVE);
        entity.setRole(finalRole);
        entity.setIsDeleted(0);
        userMapper.insert(entity);
        entity.setPasswd(null);
        return entity;
    }

    @Override
    @Transactional
    public void updateStatus(final long userId, final String status) {
        Users dbUser = getUser(userId);
        String finalStatus;
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("状态不能为空");
        } else if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
            finalStatus = STATUS_ACTIVE;
        } else if (STATUS_DISABLED.equalsIgnoreCase(status)) {
            finalStatus = STATUS_DISABLED;
        } else {
            throw new IllegalArgumentException("状态不合法");
        }
        Users update = new Users();
        update.setId(dbUser.getId());
        update.setStatus(finalStatus);
        userMapper.updateById(update);
    }

    @Override
    @Transactional
    public void resetPassword(final long userId, final String newPassword) {
        if (newPassword == null || newPassword.length() < accountAuthProperties.getMinPasswordLength()) {
            throw new IllegalArgumentException("密码长度不足");
        }
        Users dbUser = getUser(userId);
        Users update = new Users();
        update.setId(dbUser.getId());
        update.setPasswordHash(passwordHasher.hash(newPassword));
        update.setPasswd(null);
        userMapper.updateById(update);
    }

    @Override
    public Users getUser(final long userId) {
        Users dbUser = userMapper.selectById(userId);
        if (dbUser == null || (dbUser.getIsDeleted() != null && dbUser.getIsDeleted() == 1)) {
            throw new IllegalArgumentException("用户不存在");
        }
        dbUser.setPasswd(null);
        dbUser.setPasswordHash(null);
        return dbUser;
    }
}
