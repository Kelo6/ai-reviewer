package com.ai.reviewer.backend.domain.config;

import com.ai.reviewer.backend.domain.scoring.ScoringService;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.model.Scores;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置系统集成测试。
 */
@SpringBootTest
class ConfigIntegrationTest {

    private ConfigService configService;
    private ScoringService scoringService;
    private Validator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configService = new ConfigService(validator);
        scoringService = new ScoringService();
    }

    @Test
    void testFullConfigurationWorkflow() throws Exception {
        // Given - 创建自定义配置文件
        String customConfig = """
            provider: gitlab
            llm:
              adapters: [local-qwen]
              budget_usd: 0.15
            scoring:
              weights:
                SECURITY: 0.40
                QUALITY: 0.30
                MAINTAINABILITY: 0.15
                PERFORMANCE: 0.10
                TEST_COVERAGE: 0.05
              severityPenalty:
                INFO: 0.5
                MINOR: 2.0
                MAJOR: 5.0
                CRITICAL: 10.0
              ignoreConfidenceBelow: 0.4
            report:
              export:
                sarif: true
                json: false
                pdf: false
                html: true
            """;

        Path configFile = tempDir.resolve(".ai-review.yml");
        Files.write(configFile, customConfig.getBytes());

        // When - 加载配置
        AiReviewConfig config = configService.loadConfig(tempDir.toString());

        // Then - 验证配置加载
        assertNotNull(config);
        assertEquals("gitlab", config.provider());
        assertEquals(1, config.llm().adapters().size());
        assertEquals("local-qwen", config.llm().adapters().get(0));
        assertEquals(0.15, config.llm().budgetUsd(), 0.001);
        
        // 验证评分配置
        assertEquals(0.40, config.scoring().weights().get(Dimension.SECURITY), 0.001);
        assertEquals(0.4, config.scoring().ignoreConfidenceBelow(), 0.001);
        
        // 验证报告配置
        assertTrue(config.report().export().sarif());
        assertFalse(config.report().export().json());
        assertFalse(config.report().export().pdf());
        assertTrue(config.report().export().html());

        // When - 使用配置进行评分
        List<Finding> testFindings = createTestFindings();
        Scores scores = scoringService.calculateScores(testFindings, 100, config.scoring());

        // Then - 验证评分结果
        assertNotNull(scores);
        assertTrue(scores.totalScore() >= 0 && scores.totalScore() <= 100);
        
        // 验证权重被正确使用
        assertEquals(config.scoring().weights(), scores.weights());
        
        // 验证维度分数
        for (Dimension dimension : Dimension.values()) {
            assertTrue(scores.dimensions().containsKey(dimension));
            double score = scores.dimensions().get(dimension);
            assertTrue(score >= 0 && score <= 100, 
                "Score for " + dimension + " should be between 0-100, got: " + score);
        }
    }

    @Test
    void testDefaultConfigurationFallback() throws Exception {
        // Given - 不存在配置文件的目录
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);

        // When - 尝试加载配置（应该回退到默认配置）
        AiReviewConfig config = configService.loadConfig(emptyDir.toString());

        // Then - 验证使用了默认配置
        assertNotNull(config);
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        assertEquals(defaultConfig.provider(), config.provider());
        assertEquals(defaultConfig.llm().budgetUsd(), config.llm().budgetUsd(), 0.001);
        assertEquals(defaultConfig.scoring().ignoreConfidenceBelow(), config.scoring().ignoreConfidenceBelow(), 0.001);

        // 验证默认配置可以正常工作
        List<Finding> testFindings = createTestFindings();
        Scores scores = scoringService.calculateScores(testFindings, 100, config.scoring());
        
        assertNotNull(scores);
        assertTrue(scores.totalScore() >= 0 && scores.totalScore() <= 100);
    }

    @Test
    void testConfigurationValidation() throws Exception {
        // Given - 创建无效配置文件（权重和不为1）
        String invalidConfig = """
            provider: github
            llm:
              adapters: [gpt4o]
              budget_usd: 0.25
            scoring:
              weights:
                SECURITY: 0.50
                QUALITY: 0.30
                MAINTAINABILITY: 0.20
                PERFORMANCE: 0.15
                TEST_COVERAGE: 0.10
              severityPenalty:
                INFO: 1.0
                MINOR: 3.0
                MAJOR: 7.0
                CRITICAL: 12.0
              ignoreConfidenceBelow: 0.3
            report:
              export:
                sarif: true
                json: true
                pdf: true
                html: true
            """;

        Path configFile = tempDir.resolve(".ai-review.yml");
        Files.write(configFile, invalidConfig.getBytes());

        // When - 尝试加载无效配置（应该回退到默认配置）
        AiReviewConfig config = configService.loadConfig(tempDir.toString());

        // Then - 应该加载成功（使用默认配置）
        assertNotNull(config);
        // 由于配置无效，应该回退到默认配置
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        assertEquals(defaultConfig.provider(), config.provider());
    }

    @Test
    void testMinimalConfiguration() throws Exception {
        // Given - 最小配置文件
        String minimalConfig = """
            provider: github
            llm:
              adapters: [gpt4o]
              budget_usd: 0.25
            scoring:
              weights:
                SECURITY: 0.30
                QUALITY: 0.25
                MAINTAINABILITY: 0.20
                PERFORMANCE: 0.15
                TEST_COVERAGE: 0.10
              severityPenalty:
                INFO: 1.0
                MINOR: 3.0
                MAJOR: 7.0
                CRITICAL: 12.0
              ignoreConfidenceBelow: 0.3
            report:
              export:
                sarif: true
                json: false
                pdf: false
                html: false
            """;

        Path configFile = tempDir.resolve(".ai-review.yml");
        Files.write(configFile, minimalConfig.getBytes());

        // When
        AiReviewConfig config = configService.loadConfig(tempDir.toString());

        // Then
        assertNotNull(config);
        assertTrue(config.report().export().sarif());
        assertFalse(config.report().export().json());
        assertFalse(config.report().export().pdf());
        assertFalse(config.report().export().html());
        
        // 验证至少有一种格式被启用，所以配置应该有效
        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void testConfigurationPersistence() throws Exception {
        // Given
        AiReviewConfig originalConfig = AiReviewConfig.getDefault();
        Path outputPath = tempDir.resolve("saved-config.yml");

        // When - 保存配置
        configService.saveConfig(originalConfig, outputPath);
        
        // Then - 重新加载并验证
        assertTrue(Files.exists(outputPath));
        
        // 从保存的文件中重新加载配置
        Path parentDir = outputPath.getParent();
        Path configFile = parentDir.resolve(".ai-review.yml");
        Files.move(outputPath, configFile);
        
        AiReviewConfig reloadedConfig = configService.loadConfig(parentDir.toString());
        
        assertNotNull(reloadedConfig);
        assertEquals(originalConfig.provider(), reloadedConfig.provider());
        assertEquals(originalConfig.llm().budgetUsd(), reloadedConfig.llm().budgetUsd(), 0.001);
        assertEquals(originalConfig.scoring().ignoreConfidenceBelow(), 
                    reloadedConfig.scoring().ignoreConfidenceBelow(), 0.001);
    }

    /**
     * 创建测试用的问题发现。
     */
    private List<Finding> createTestFindings() {
        return List.of(
            new Finding(
                "TEST-001",
                "src/main/java/Test.java",
                10, 15,
                Severity.MAJOR,
                Dimension.SECURITY,
                "Test security issue",
                "Security vulnerability detected",
                "Fix the security issue",
                null,
                List.of("test-analyzer"),
                0.8
            ),
            new Finding(
                "TEST-002",
                "src/main/java/Test.java",
                20, 25,
                Severity.MINOR,
                Dimension.QUALITY,
                "Test quality issue",
                "Code quality issue detected",
                "Improve code quality",
                null,
                List.of("test-analyzer"),
                0.6
            ),
            new Finding(
                "TEST-003",
                "src/main/java/Test.java",
                30, 35,
                Severity.INFO,
                Dimension.MAINTAINABILITY,
                "Test maintainability suggestion",
                "Maintainability improvement suggested",
                "Consider refactoring",
                null,
                List.of("test-analyzer"),
                0.4
            )
        );
    }
}
