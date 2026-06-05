package com.github.mail.service.User;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.mail.repo.User.domain.Users;

public interface IAccountAdminService {

    Page<Users> listUsers(long page, long size);

    Users createUser(String username, String password, String role);

    void updateStatus(long userId, String status);

    void resetPassword(long userId, String newPassword);

    Users getUser(long userId);
}
