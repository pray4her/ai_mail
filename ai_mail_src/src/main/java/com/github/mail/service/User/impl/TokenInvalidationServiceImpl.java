package com.github.mail.service.User.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.mail.repo.User.domain.TokenInvalidation;
import com.github.mail.repo.User.mapper.TokenInvalidationMapper;
import com.github.mail.service.User.ITokenInvalidationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TokenInvalidationServiceImpl extends ServiceImpl<TokenInvalidationMapper, TokenInvalidation> implements ITokenInvalidationService {

    @Override
    public void invalidate(final String jti, final LocalDateTime expiresAt) {
        TokenInvalidation entity = new TokenInvalidation();
        entity.setJti(jti);
        entity.setExpiresAt(expiresAt);
        this.save(entity);
    }

    @Override
    public boolean isInvalidated(final String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        QueryWrapper<TokenInvalidation> wrapper = new QueryWrapper<>();
        wrapper.eq("jti", jti);
        return this.count(wrapper) > 0;
    }

    @Override
    public int cleanupExpired(final LocalDateTime now) {
        QueryWrapper<TokenInvalidation> wrapper = new QueryWrapper<>();
        wrapper.lt("expires_at", now);
        return this.getBaseMapper().delete(wrapper);
    }
}
