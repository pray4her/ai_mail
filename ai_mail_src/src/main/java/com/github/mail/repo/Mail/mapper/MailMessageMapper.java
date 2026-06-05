package com.github.mail.repo.Mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.Mail.domain.MailMessage;
import org.apache.ibatis.annotations.Mapper;

/**
* @author Asteries
* @description 针对表【mail_message】的数据库操作Mapper
* @createDate 2025-12-24 11:20:24
* @Entity generator.domain.MailMessage
*/
@Mapper
public interface MailMessageMapper extends BaseMapper<MailMessage> {

}




