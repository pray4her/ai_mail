package com.github.mail.repo.User.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.User.domain.TokenInvalidation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenInvalidationMapper extends BaseMapper<TokenInvalidation> {
}
