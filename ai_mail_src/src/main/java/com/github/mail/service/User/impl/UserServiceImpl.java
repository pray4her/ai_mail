package com.github.mail.service.User.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.mail.repo.User.domain.Users;
import com.github.mail.repo.User.mapper.UserMapper;
import com.github.mail.service.User.IAccountAuthService;
import com.github.mail.service.User.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户登录服务
 * @author Aster
 * @date 2026/1/9
 */


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, Users> implements IUserService {

    private final IAccountAuthService accountAuthService;

    @Autowired
    public UserServiceImpl(final IAccountAuthService accountAuthService) {
        this.accountAuthService = accountAuthService;
    }

    @Override
    public String verifyUser(Users user) {
        try {
            return accountAuthService.login(user.getUser(), user.getPasswd());
        } catch (Exception e) {
            return "用户名或密码错误";
        }
    }

}
