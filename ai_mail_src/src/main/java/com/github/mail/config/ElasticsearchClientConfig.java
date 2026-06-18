package com.github.mail.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.mail.model.config.Properties.ElasticsearchClientProperties;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Elasticsearch 配置
 * <p>
 * 使用官方 Java API Client (8.x)
 *
 * @author Aster
 * @date 2025/12/30
 */
@Configuration
public class ElasticsearchClientConfig {

    private final ElasticsearchClientProperties elasticsearchClientProperties;

    public ElasticsearchClientConfig(ElasticsearchClientProperties elasticsearchClientProperties) {
        this.elasticsearchClientProperties = elasticsearchClientProperties;
    }


    @Bean
    public ElasticsearchClient elasticsearchClient() throws Exception {
        String password = elasticsearchClientProperties.getPassword();
        String username = elasticsearchClientProperties.getUsername();
        String host = elasticsearchClientProperties.getHost();
        int port = elasticsearchClientProperties.getPort();
        String scheme = elasticsearchClientProperties.getScheme();

        // 配置认证
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (password != null && !password.isEmpty()) {
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
            );
        }
        SSLContext sslContext = "https".equalsIgnoreCase(scheme) ? buildSslContext() : null;

        // 创建 RestClient
        RestClient restClient = RestClient.builder(
                        new HttpHost(host, port, scheme))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    if (sslContext != null) {
                        httpClientBuilder.setSSLContext(sslContext);
                    }
                    return httpClientBuilder;
                }).build();

        // 创建 Transport
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        // 创建 ElasticsearchClient
        return new ElasticsearchClient(transport);
    }

    private SSLContext buildSslContext() throws Exception {
        ClassPathResource resource = new ClassPathResource("es/http_ca.crt");
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Certificate trustedCa;
        try (InputStream is = resource.getInputStream()) {
            trustedCa = factory.generateCertificate(is);
        }

        KeyStore trustStore = KeyStore.getInstance("pkcs12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", trustedCa);

        SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                .loadTrustMaterial(trustStore, null);
        return sslContextBuilder.build();
    }
}
