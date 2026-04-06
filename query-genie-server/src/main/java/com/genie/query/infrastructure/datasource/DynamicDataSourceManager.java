package com.genie.query.infrastructure.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.genie.query.domain.schema.model.DbDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;

/**
 * 动态数据源管理器（I2-B5）：按需创建并缓存业务数据库连接池（Druid），支持多数据源。
 *
 * <p>密码以 AES-128 加密存储，解密密钥通过环境变量 {@code DB_ENCRYPT_KEY} 注入。
 * 若密钥为空，则假定密码未加密（仅开发环境使用）。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Component
public class DynamicDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    private static final String AES_ALGORITHM = "AES";
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_ACTIVE = 5;
    private static final int INITIAL_SIZE = 1;

    /** AES 解密密钥（Base64 编码，16 字节，通过环境变量注入） */
    @Value("${app.datasource.encrypt-key:}")
    private String encryptKey;

    private final ConcurrentMap<Long, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定数据源的连接池。
     *
     * @param config 数据源配置（来自 db_datasource 表）
     * @return 对应的 DataSource
     */
    public DataSource getOrCreate(DbDatasource config) {
        return dataSourceMap.computeIfAbsent(config.getId(), id -> createDataSource(config));
    }

    /**
     * 移除已缓存的数据源连接池（配置更新后调用）。
     */
    public void remove(Long datasourceId) {
        DataSource removed = dataSourceMap.remove(datasourceId);
        if (removed instanceof DruidDataSource druid) {
            druid.close();
            log.info("[DynamicDS] 已关闭数据源 id={}", datasourceId);
        }
    }

    private DataSource createDataSource(DbDatasource config) {
        log.info("[DynamicDS] 创建数据源 id={}, name={}", config.getId(), config.getName());
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(config.getDbUrl());
        ds.setUsername(config.getDbUsername());
        ds.setPassword(decrypt(config.getDbPassword()));
        ds.setMaxActive(MAX_ACTIVE);
        ds.setInitialSize(INITIAL_SIZE);
        ds.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        ds.setSocketTimeout(QUERY_TIMEOUT_SECONDS * 1000);
        ds.setValidationQuery("SELECT 1");
        ds.setTestWhileIdle(true);
        ds.setMinEvictableIdleTimeMillis(60000);
        try {
            ds.init();
        } catch (Exception e) {
            log.error("[DynamicDS] 数据源初始化失败 id={}: {}", config.getId(), e.getMessage());
            throw new RuntimeException("数据源 [" + config.getName() + "] 初始化失败: " + e.getMessage(), e);
        }
        return ds;
    }

    /**
     * AES 解密密码。若 encryptKey 为空则原文返回（开发环境无加密）。
     */
    String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return encryptedPassword;
        }
        if (encryptKey == null || encryptKey.isBlank()) {
            return encryptedPassword;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decrypted);
        } catch (Exception e) {
            log.error("[DynamicDS] 密码解密失败，使用原文: {}", e.getMessage());
            return encryptedPassword;
        }
    }
}
