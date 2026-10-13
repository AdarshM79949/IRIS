package com.chatapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            if (datasourceUrl != null && datasourceUrl.contains(":sqlite:")) {
                hibernateProperties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
            } else if (datasourceUrl != null && datasourceUrl.contains(":postgresql:")) {
                hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            }
        };
    }
}
