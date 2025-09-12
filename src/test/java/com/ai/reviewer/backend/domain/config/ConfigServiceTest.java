package com.ai.reviewer.backend.domain.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ConfigService 测试。
 */
class ConfigServiceTest {

    @Mock
    private Validator validator;

    private ConfigService configService;

    @TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configService = new ConfigService(validator);
        
        // 模拟验证器返回无错误
        when(validator.validate(any(AiReviewConfig.class))).thenReturn(Set.of());
    }

    @Test
    void testLoadConfigFromValidYaml() throws Exception {
        // Given
        String yamlContent = """
            provider: github
            llm:
              adapters: [gpt4o, claude35]
              budget_usd: 0.50
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
                json: true
                pdf: true
                html: true
            """;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes())) {
            // When
            AiReviewConfig config = configService.loadConfig(inputStream);

            // Then
            assertNotNull(config);
            assertEquals("github", config.provider());
            assertEquals(2, config.llm().adapters().size());
            assertTrue(config.llm().adapters().contains("gpt4o"));
            assertTrue(config.llm().adapters().contains("claude35"));
            assertEquals(0.50, config.llm().budgetUsd(), 0.001);
            assertEquals(0.30, config.scoring().weights().get(com.ai.reviewer.shared.enums.Dimension.SECURITY), 0.001);
            assertEquals(0.3, config.scoring().ignoreConfidenceBelow(), 0.001);
            assertTrue(config.report().export().sarif());
            assertTrue(config.report().export().json());
            assertTrue(config.report().export().pdf());
            assertTrue(config.report().export().html());
        }
    }

    @Test
    void testLoadConfigFromFile() throws Exception {
        // Given
        String yamlContent = """
            provider: gitlab
            llm:
              adapters: [local-qwen]
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
              ignoreConfidenceBelow: 0.4
            report:
              export:
                sarif: false
                json: true
                pdf: false
                html: true
            """;

        java.nio.file.Path configFile = tempDir.resolve(".ai-review.yml");
        Files.write(configFile, yamlContent.getBytes());

        // When
        AiReviewConfig config = configService.loadConfig(tempDir.toString());

        // Then
        assertNotNull(config);
        assertEquals("gitlab", config.provider());
        assertEquals(1, config.llm().adapters().size());
        assertEquals("local-qwen", config.llm().adapters().get(0));
        assertEquals(0.25, config.llm().budgetUsd(), 0.001);
        assertEquals(0.4, config.scoring().ignoreConfidenceBelow(), 0.001);
        assertFalse(config.report().export().sarif());
        assertTrue(config.report().export().json());
        assertFalse(config.report().export().pdf());
        assertTrue(config.report().export().html());
    }

    @Test
    void testLoadConfigWhenFileNotFound() {
        // When - 文件不存在时应返回默认配置
        AiReviewConfig config = configService.loadConfig(tempDir.toString());

        // Then
        assertNotNull(config);
        // 应该返回默认配置
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        assertEquals(defaultConfig.provider(), config.provider());
        assertEquals(defaultConfig.llm().budgetUsd(), config.llm().budgetUsd());
    }

    @Test
    void testLoadConfigWithNullPath() {
        // When - 路径为null时应返回默认配置
        AiReviewConfig config = configService.loadConfig((String) null);

        // Then
        assertNotNull(config);
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        assertEquals(defaultConfig.provider(), config.provider());
    }

    @Test
    void testLoadConfigWithEmptyPath() {
        // When - 路径为空时应返回默认配置
        AiReviewConfig config = configService.loadConfig("   ");

        // Then
        assertNotNull(config);
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        assertEquals(defaultConfig.provider(), config.provider());
    }

    @Test
    void testLoadConfigWithValidationErrors() throws Exception {
        // Given
        ConstraintViolation<AiReviewConfig> violation = createMockViolation("provider", "Invalid provider");
        when(validator.validate(any(AiReviewConfig.class))).thenReturn(Set.of(violation));

        String yamlContent = """
            provider: invalid-provider
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
                json: true
                pdf: true
                html: true
            """;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes())) {
            // When & Then
            assertThrows(ConfigValidationException.class, () -> {
                configService.loadConfig(inputStream);
            });
        }
    }

    @Test
    void testHasConfigFile() throws Exception {
        // Given
        java.nio.file.Path configFile = tempDir.resolve(".ai-review.yml");
        Files.write(configFile, "provider: github".getBytes());

        // When & Then
        assertTrue(configService.hasConfigFile(tempDir.toString()));
        assertFalse(configService.hasConfigFile(tempDir.resolve("nonexistent").toString()));
    }

    @Test
    void testGetConfigPath() {
        // When
        java.nio.file.Path configPath = configService.getConfigPath(tempDir.toString());

        // Then
        assertEquals(tempDir.resolve(".ai-review.yml"), configPath);
    }

    @Test
    void testCreateExampleConfig() throws Exception {
        // Given
        java.nio.file.Path outputPath = tempDir.resolve("example-config.yml");

        // When
        configService.createExampleConfig(outputPath);

        // Then
        assertTrue(Files.exists(outputPath));
        String content = Files.readString(outputPath);
        assertFalse(content.isEmpty());
        assertTrue(content.contains("provider:"));
        assertTrue(content.contains("llm:"));
        assertTrue(content.contains("scoring:"));
        assertTrue(content.contains("report:"));
    }

    @Test
    void testSaveConfig() throws Exception {
        // Given
        AiReviewConfig config = AiReviewConfig.getDefault();
        Path outputPath = tempDir.resolve("saved-config.yml");

        // When
        configService.saveConfig(config, outputPath);

        // Then
        assertTrue(Files.exists(outputPath));
        
        // 验证保存的配置可以重新加载
        AiReviewConfig reloadedConfig = configService.loadConfig(tempDir.toString() + "/saved-config.yml");
        assertNotNull(reloadedConfig);
        assertEquals(config.provider(), reloadedConfig.provider());
    }

    @Test
    void testMergeConfig() {
        // Given
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
        AiReviewConfig overrideConfig = new AiReviewConfig(
            "gitlab",
            null, // 不覆盖LLM配置
            null, // 不覆盖评分配置
            null  // 不覆盖报告配置
        );

        // When
        AiReviewConfig mergedConfig = configService.mergeConfig(defaultConfig, overrideConfig);

        // Then
        assertEquals("gitlab", mergedConfig.provider()); // 被覆盖
        assertEquals(defaultConfig.llm(), mergedConfig.llm()); // 使用默认值
        assertEquals(defaultConfig.scoring(), mergedConfig.scoring()); // 使用默认值
        assertEquals(defaultConfig.report(), mergedConfig.report()); // 使用默认值
    }

    /**
     * 创建模拟的约束违规。
     */
    @SuppressWarnings("unchecked")
    private ConstraintViolation<AiReviewConfig> createMockViolation(String propertyPath, String message) {
        ConstraintViolation<AiReviewConfig> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn(propertyPath);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }
}
