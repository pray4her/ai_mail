package com.github.mail.service.User;

import com.github.mail.repo.User.domain.Users;

/**
 * 用户服务接口
 * @author Aster
 */
public interface IUserService {

    //验证用户登录
    String verifyUser(Users user);
}
