package com.ai.reviewer.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * AI Reviewer Backend Application.
 * 
 * <p>This is the main entry point for the AI code review backend service.
 * It provides REST APIs for code review orchestration, SCM integration,
 * scoring engine, and report generation.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.ai.reviewer.backend.infra.jpa.entity")
@EnableJpaRepositories(basePackages = "com.ai.reviewer.backend.infra.jpa.repository")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
