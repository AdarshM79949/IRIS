package com.chatapp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:jdbc:sqlite:chat.db}")
    private String configuredUrl;

    @Value("${spring.datasource.username:}")
    private String configuredUsername;

    @Value("${spring.datasource.password:}")
    private String configuredPassword;

    private volatile boolean isFallbackSqlite = false;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (configuredUrl != null && configuredUrl.contains(":postgresql:")) {
            log.info("Testing connection to PostgreSQL database: {}", configuredUrl);
            try {
                DriverManager.setLoginTimeout(5);
                try (Connection conn = DriverManager.getConnection(configuredUrl, configuredUsername, configuredPassword)) {
                    log.info("✅ Successfully connected to PostgreSQL/Supabase database!");
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(configuredUrl);
                    config.setUsername(configuredUsername);
                    config.setPassword(configuredPassword);
                    config.setDriverClassName("org.postgresql.Driver");
                    config.setMaximumPoolSize(10);
                    config.setConnectionTimeout(10000);
                    return new HikariDataSource(config);
                }
            } catch (Throwable e) {
                log.warn("⚠️ PostgreSQL connection failed (e.g. Supabase paused or network issue): {}. Falling back to SQLite with Discord Cloud Sync!", e.getMessage());
                isFallbackSqlite = true;
            }
        }

        // SQLite DataSource fallback
        log.info("Using SQLite database (jdbc:sqlite:chat.db) with Discord cloud sync");
        isFallbackSqlite = true;
        HikariConfig sqliteConfig = new HikariConfig();
        sqliteConfig.setJdbcUrl("jdbc:sqlite:chat.db");
        sqliteConfig.setDriverClassName("org.sqlite.JDBC");
        sqliteConfig.setMaximumPoolSize(1);
        sqliteConfig.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(sqliteConfig);
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            if (isFallbackSqlite || configuredUrl == null || configuredUrl.contains(":sqlite:")) {
                hibernateProperties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
            } else if (configuredUrl.contains(":postgresql:")) {
                hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            }
        };
    }
}
