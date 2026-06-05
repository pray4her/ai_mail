package com.github.mail.repo.User.domain;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * 用户表
 * @author Asteries
 */
@Data
@TableName("users")
public class Users {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    //用户名
    @TableField("user")
    private String user;

    //用户密码
    @TableField("passwd")
    private String passwd;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("status")
    private String status;

    @TableField("role")
    private String role;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
