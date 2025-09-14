package com.ai.reviewer.backend.config;

import com.ai.reviewer.backend.api.controller.ReportController;
import com.ai.reviewer.backend.api.exception.GlobalExceptionHandler;
import com.ai.reviewer.backend.domain.orchestrator.report.BatchReportExporter;
import com.ai.reviewer.backend.domain.orchestrator.report.ReportConfigManager;
import com.ai.reviewer.backend.domain.report.ReportService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.mockito.Mockito;

/**
 * 专为ReportController测试设计的独立配置类。
 * 不包含任何JPA或数据库相关的配置，避免复杂的Bean依赖问题。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class ReportControllerTestConfig {
    
    @Bean
    public ReportService reportService() {
        return Mockito.mock(ReportService.class);
    }
    
    @Bean
    public BatchReportExporter batchReportExporter() {
        return Mockito.mock(BatchReportExporter.class);
    }
    
    @Bean
    public ReportConfigManager reportConfigManager() {
        return Mockito.mock(ReportConfigManager.class);
    }
    
    @Bean
    public ReportController reportController(ReportService reportService,
                                           BatchReportExporter batchReportExporter,
                                           ReportConfigManager reportConfigManager) {
        return new ReportController(reportService, batchReportExporter, reportConfigManager);
    }
    
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
