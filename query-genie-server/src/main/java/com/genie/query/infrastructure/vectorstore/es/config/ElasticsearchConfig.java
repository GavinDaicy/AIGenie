package com.genie.query.infrastructure.vectorstore.es.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:http://localhost:9200}")
    private String elasticsearchHost;
    @Value("${elasticsearch.username:elastic}")
    private String userName;
    @Value("${elasticsearch.password:your_password}")
    private String password;

    @Bean
    public ElasticsearchClient elasticsearchClient() {

        // 配置凭证
//        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
//        credsProv.setCredentials(
//                AuthScope.ANY,
//                new UsernamePasswordCredentials(userName, password)
//        );

        // 构建底层 REST Client
        RestClient restClient = RestClient
                .builder(HttpHost.create(elasticsearchHost))
//                .setHttpClientConfigCallback(httpClientBuilder ->
//                        httpClientBuilder.setDefaultCredentialsProvider(credsProv)
//                )
                .build();

        // 使用Jackson映射器创建传输层
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // 创建并返回API Client
        return new ElasticsearchClient(transport);
    }
}