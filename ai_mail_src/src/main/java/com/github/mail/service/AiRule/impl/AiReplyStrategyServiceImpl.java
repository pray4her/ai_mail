package com.github.mail.service.AiRule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.mail.repo.AiRule.domain.AiReplyStrategy;
import com.github.mail.repo.AiRule.dto.AiReplyStrategyDTO;
import com.github.mail.repo.AiRule.mapper.AiReplyStrategyMapper;
import com.github.mail.service.AiRule.AiReplyStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author Asteries
 * @description 针对表【ai_reply_strategy(AI 回复策略表)】的数据库操作Service实现
 * @createDate 2026-01-05 11:55:45
 */
@Service
@RequiredArgsConstructor
public class AiReplyStrategyServiceImpl extends ServiceImpl<AiReplyStrategyMapper, AiReplyStrategy>
        implements AiReplyStrategyService {


    @Override
    public AiReplyStrategy getCurrentStrategy() {
        LambdaQueryWrapper<AiReplyStrategy> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(AiReplyStrategy::getUpdatedTime);
        queryWrapper.last("LIMIT 1");
        return this.getOne(queryWrapper);
    }

    @Override
    public AiReplyStrategyDTO getCurrentStrategyDTO() {
        LambdaQueryWrapper<AiReplyStrategy> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(AiReplyStrategy::getUpdatedTime);
        queryWrapper.last("LIMIT 1");
        AiReplyStrategy strategy = this.getOne(queryWrapper);
        return strategy != null ? convertToDTO(strategy) : null;
    }

    @Override
    @Transactional
    public boolean saveOrUpdateStrategy(AiReplyStrategyDTO strategyDTO) {
        AiReplyStrategy strategy = convertToEntity(strategyDTO);
        if (strategy.getId() == null) {
            // 新增策略
            strategy.setCreatedTime(LocalDateTime.now());
            strategy.setUpdatedTime(LocalDateTime.now());
        } else {
            // 更新策略
            strategy.setUpdatedTime(LocalDateTime.now());
        }
        return this.saveOrUpdate(strategy);
    }

    /**
     * 转换为DTO
     */
    private AiReplyStrategyDTO convertToDTO(AiReplyStrategy entity) {
        AiReplyStrategyDTO dto = new AiReplyStrategyDTO();
        BeanUtils.copyProperties(entity, dto);
        // 转换Integer为Boolean
        dto.setIncludeSteps(entity.getIncludeSteps() != null && entity.getIncludeSteps() == 1);
        return dto;
    }

    /**
     * 转换为实体
     */
    private AiReplyStrategy convertToEntity(AiReplyStrategyDTO dto) {
        AiReplyStrategy entity = new AiReplyStrategy();
        BeanUtils.copyProperties(dto, entity);
        // 转换Boolean为Integer
        entity.setIncludeSteps(dto.getIncludeSteps() != null && dto.getIncludeSteps() ? 1 : 0);
        return entity;
    }
}
