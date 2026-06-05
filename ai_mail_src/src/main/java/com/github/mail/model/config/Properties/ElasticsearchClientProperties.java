package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Es基本配置 （yml读取）
 * @author Aster
 * @date 2026/1/6
 */

@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch.client")
public class ElasticsearchClientProperties {

    //主机地址
    private String host = "localhost";
    //端口号
    private int port = 9200;
    //用户名
    private String username = "elastic";
    //https密码
    private String password = "";
    //加密策略
    private String scheme = "https";

}
