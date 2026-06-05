package com.github.mail.repo.User.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
