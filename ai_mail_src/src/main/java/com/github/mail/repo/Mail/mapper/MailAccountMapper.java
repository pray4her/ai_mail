package com.github.mail.repo.Mail.mapper;

import com.github.mail.repo.Mail.domain.MailAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 邮件账户 Mapper
 * 
 * @author Asteries
 */
@Mapper
public interface MailAccountMapper extends BaseMapper<MailAccount> {

    /**
     * 查询所有活跃的邮箱账户（未删除）
     */
    @Select("SELECT * FROM mail_account WHERE is_deleted = 0")
    List<MailAccount> selectAllActive();
}




