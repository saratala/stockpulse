package com.stockpulse.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.stockpulse.agent.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {
}