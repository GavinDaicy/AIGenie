package com.genie.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/6
 */
@Slf4j
@SpringBootApplication
@EnableCaching
public class QueryGenieApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryGenieApplication.class, args);
        log.info("-----------QueryGenieApplication Server Project Start Success.-------------");
    }
}
