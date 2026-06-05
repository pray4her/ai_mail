package com.github.mail.repo.User.dto;

import lombok.Data;

@Data
public class AdminCreateUserRequest {
    private String username;
    private String password;
    private String role;
}
