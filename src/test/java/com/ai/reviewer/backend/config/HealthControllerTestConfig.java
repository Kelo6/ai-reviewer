package com.ai.reviewer.backend.config;

import com.ai.reviewer.backend.api.controller.HealthController;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 专为HealthController测试设计的独立配置类。
 * 不包含任何JPA或数据库相关的配置，避免复杂的Bean依赖问题。
 */
@SpringBootConfiguration
@ComponentScan(basePackageClasses = HealthController.class)
public class HealthControllerTestConfig {
    
    // 这个配置类只包含必要的HealthController
    // 所有其他依赖通过@MockBean在测试类中模拟
}
