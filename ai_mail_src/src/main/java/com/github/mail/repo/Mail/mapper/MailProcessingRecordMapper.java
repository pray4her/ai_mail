package com.github.mail.repo.Mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.Mail.domain.MailProcessingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮件处理记录 Mapper
 * 
 * @author Asteries
 */
@Mapper
public interface MailProcessingRecordMapper extends BaseMapper<MailProcessingRecord> {
    // BaseMapper 已提供基本的 CRUD 操作
}
