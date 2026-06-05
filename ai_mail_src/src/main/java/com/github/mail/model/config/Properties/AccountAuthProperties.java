package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "security.account")
public class AccountAuthProperties {

    private boolean allowSelfRegisterAfterInitialized = false;

    private boolean serverLogoutEnabled = false;

    private int jwtExpirationDays = 5;

    private String jwtSecret = "";

    private String jwtIssuer = "AIMail";

    private int minPasswordLength = 8;
}
