package com.github.mail.service.AiRule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.mail.repo.AiRule.domain.AiReplyStrategy;
import com.github.mail.repo.AiRule.dto.AiReplyStrategyDTO;

/**
* @author Asteries
* @description 针对表【ai_reply_strategy(AI 回复策略表)】的数据库操作Service
* @createDate 2026-01-05 11:55:45
*/
public interface AiReplyStrategyService extends IService<AiReplyStrategy> {

    /**
     * 获取当前回复策略
     * return 回复策略
     */
    AiReplyStrategy getCurrentStrategy();

    /**
     * 获取当前回复策略交由前端
     * @return 前端回复策略传输类
     */
    AiReplyStrategyDTO getCurrentStrategyDTO();


    /**
     * 创建或更新回复策略
     * @param strategy 策略信息
     * @return 是否成功
     */
    boolean saveOrUpdateStrategy(AiReplyStrategyDTO strategy);
}
