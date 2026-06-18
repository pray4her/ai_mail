package com.github.mail.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.langfuse")
public class LangfuseProperties {

    private boolean enabled;

    private String url = "https://cloud.langfuse.com";

    private String publicKey = "";

    private String secretKey = "";

    private String promptName = "mail-auto-reply";

    private String promptLabel = "production";

    private int promptVersion;

    private String traceName = "mail-auto-reply";

    private String environment = "local";
}
