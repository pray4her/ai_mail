package com.github.mail.repo.User.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.User.domain.Users;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表Mapper
 * @author Aster
 * @date 2026/1/9
 */

@Mapper
public interface UserMapper extends BaseMapper<Users> {
}
