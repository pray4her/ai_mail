package com.github.mail.service.AiRule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.mail.repo.AiRule.domain.AiReplyRule;
import com.github.mail.repo.AiRule.dto.AiReplyRuleDTO;
import com.github.mail.repo.AiRule.dto.RuleOrderDTO;
import com.github.mail.repo.AiRule.dto.RuleVersionHistoryDTO;
import com.github.mail.repo.AiRule.mapper.AiReplyRuleMapper;
import com.github.mail.service.AiRule.AiReplyRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Asteries
 * @description 针对表【ai_reply_rule(AI 回复规则表)】的数据库操作Service实现
 * @createDate 2026-01-05 11:55:45
 */
@Service
@RequiredArgsConstructor
public class AiReplyRuleServiceImpl extends ServiceImpl<AiReplyRuleMapper, AiReplyRule>
        implements AiReplyRuleService {

    @Override
    public AiReplyRule getById(Long id) {
        LambdaQueryWrapper<AiReplyRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiReplyRule::getId, id);
        return this.getOne(queryWrapper);
    }

    @Override
    public List<AiReplyRuleDTO> getAllRuleDTO() {
        LambdaQueryWrapper<AiReplyRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(AiReplyRule::getRuleOrder);
        List<AiReplyRule> rules = this.list(queryWrapper);
        return rules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<AiReplyRule> getAllRule() {
        LambdaQueryWrapper<AiReplyRule> queryWrapper = new LambdaQueryWrapper<>();
        //获取所有非核心规则
        queryWrapper.eq(AiReplyRule::getIsCore, 0);
        queryWrapper.eq(AiReplyRule::getEnabled, 1);
        return this.list(queryWrapper);
    }

    @Override
    @Transactional
    public boolean saveOrUpdateAiReplyRule(AiReplyRuleDTO aiReplyRuleDTO) {
        AiReplyRule aiReplyRule = convertToEntity(aiReplyRuleDTO);
        if (aiReplyRule.getId() == null) {
            // 新增规则，设置默认值
            aiReplyRule.setCreatedTime(LocalDateTime.now());
            aiReplyRule.setUpdatedTime(LocalDateTime.now());
            aiReplyRule.setVersion(generateVersion());
            // 获取updateBy
        } else {
            // 更新规则，更新时间和版本号
            aiReplyRule.setUpdatedTime(LocalDateTime.now());
            aiReplyRule.setVersion(generateVersion());
        }
        return this.saveOrUpdate(aiReplyRule);
    }

    @Override
    @Transactional
    public boolean saveOrUpdateRuleList(List<AiReplyRule> aiReplyRuleList) {
        return this.saveOrUpdateBatch(aiReplyRuleList);
    }

    @Override
    @Transactional
    public boolean removeById(AiReplyRule aiReplyRule) {
        return this.removeById(aiReplyRule.getId());
    }

    @Override
    @Transactional
    public boolean reorderRules(List<RuleOrderDTO> ruleOrders) {
        for (RuleOrderDTO ruleOrder : ruleOrders) {
            AiReplyRule rule = this.getById(ruleOrder.getId());
            if (rule != null) {
                rule.setRuleOrder(ruleOrder.getRuleOrder());
                rule.setUpdatedTime(LocalDateTime.now());
                this.updateById(rule);
            }
        }
        return true;
    }

    @Override
    public List<RuleVersionHistoryDTO> getRuleVersionHistory() {
        // 这里可以实现版本历史的查询逻辑
        // 简化实现，返回当前版本信息
        List<RuleVersionHistoryDTO> history = new ArrayList<>();
        LambdaQueryWrapper<AiReplyRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(AiReplyRule::getUpdatedTime);
        // 限制返回最近的10条记录
        queryWrapper.last("LIMIT 10");
        List<AiReplyRule> rules = this.list(queryWrapper);
        
        for (AiReplyRule rule : rules) {
            RuleVersionHistoryDTO dto = new RuleVersionHistoryDTO();
            dto.setRuleOrder(rule.getRuleOrder());
            dto.setVersion(rule.getVersion());
            dto.setUpdatedBy(rule.getUpdatedBy());
            dto.setUpdatedTime(rule.getUpdatedTime());
            history.add(dto);
        }
        return history;
    }


    /**
     * 转换为DTO
     */
    private AiReplyRuleDTO convertToDTO(AiReplyRule entity) {
        AiReplyRuleDTO dto = new AiReplyRuleDTO();
        BeanUtils.copyProperties(entity, dto);
        // 转换Integer为Boolean
        dto.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        dto.setIsCore(entity.getIsCore() != null && entity.getIsCore() == 1);
        return dto;
    }

    /**
     * 转换为实体
     */
    private AiReplyRule convertToEntity(AiReplyRuleDTO dto) {
        AiReplyRule entity = new AiReplyRule();
        BeanUtils.copyProperties(dto, entity);
        // 转换Boolean为Integer
        entity.setEnabled(dto.getEnabled() != null && dto.getEnabled() ? 1 : 0);
        entity.setIsCore(dto.getIsCore() != null && dto.getIsCore() ? 1 : 0);
        return entity;
    }

    /**
     * 生成版本号
     */
    private String generateVersion() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
