package com.ai.reviewer.backend.config;

import com.ai.reviewer.backend.api.controller.ReviewController;
import com.ai.reviewer.backend.api.exception.GlobalExceptionHandler;
import com.ai.reviewer.backend.domain.orchestrator.ReviewOrchestrator;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;

/**
 * 专为ReviewController测试设计的独立配置类。
 * 不包含任何JPA或数据库相关的配置，避免复杂的Bean依赖问题。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class ReviewControllerTestConfig {
    
    @Bean
    public ReviewController reviewController(ReviewOrchestrator reviewOrchestrator) {
        return new ReviewController(reviewOrchestrator);
    }
    
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    
    @MockBean
    private ReviewOrchestrator reviewOrchestrator;
}
