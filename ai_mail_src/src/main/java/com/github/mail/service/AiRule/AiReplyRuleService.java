package com.github.mail.service.AiRule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.mail.repo.AiRule.domain.AiReplyRule;
import com.github.mail.repo.AiRule.dto.AiReplyRuleDTO;
import com.github.mail.repo.AiRule.dto.RuleOrderDTO;
import com.github.mail.repo.AiRule.dto.RuleVersionHistoryDTO;

import java.util.List;

/**
 * @author Asteries
 * @description 针对表【ai_reply_rule(AI 回复规则表)】的数据库操作Service
 * @createDate 2026-01-05 11:55:45
 */
public interface AiReplyRuleService extends IService<AiReplyRule> {

    /**
     * curd （增删查改）
     */
    //根据id获取规则
    AiReplyRule getById(Long id);

    //获取所有规则交由前端显示
    List<AiReplyRuleDTO> getAllRuleDTO();

    //获取所有规则
    List<AiReplyRule> getAllRule();

    //根据前端传来的规则保存或更新规则
    boolean saveOrUpdateAiReplyRule(AiReplyRuleDTO aiReplyRule);

    //批量保存或更新规则
    boolean saveOrUpdateRuleList(List<AiReplyRule> aiReplyRuleList);

    //删除规则 controller未使用
    @Override
    boolean removeById(AiReplyRule aiReplyRule);


    /**
     * 调整规则顺序
     * @param ruleOrders 规则顺序列表
     * @return 是否成功
     */
    boolean reorderRules(List<RuleOrderDTO> ruleOrders);

    /**
     * 获取规则版本历史
     * @return 版本历史列表
     */
    List<RuleVersionHistoryDTO> getRuleVersionHistory();

}
