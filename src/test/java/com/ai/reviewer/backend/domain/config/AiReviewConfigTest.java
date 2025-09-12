package com.ai.reviewer.backend.domain.config;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiReviewConfig 测试。
 */
class AiReviewConfigTest {

    @Test
    void testDefaultConfig() {
        // When
        AiReviewConfig defaultConfig = AiReviewConfig.getDefault();

        // Then
        assertNotNull(defaultConfig);
        assertEquals("github", defaultConfig.provider());
        
        // LLM 配置
        assertNotNull(defaultConfig.llm());
        assertEquals(2, defaultConfig.llm().adapters().size());
        assertTrue(defaultConfig.llm().adapters().contains("gpt-4o"));
        assertTrue(defaultConfig.llm().adapters().contains("claude-3.5-sonnet"));
        assertEquals(0.50, defaultConfig.llm().budgetUsd(), 0.001);
        
        // 评分配置
        assertNotNull(defaultConfig.scoring());
        assertEquals(0.30, defaultConfig.scoring().weights().get(Dimension.SECURITY), 0.001);
        assertEquals(0.25, defaultConfig.scoring().weights().get(Dimension.QUALITY), 0.001);
        assertEquals(0.20, defaultConfig.scoring().weights().get(Dimension.MAINTAINABILITY), 0.001);
        assertEquals(0.15, defaultConfig.scoring().weights().get(Dimension.PERFORMANCE), 0.001);
        assertEquals(0.10, defaultConfig.scoring().weights().get(Dimension.TEST_COVERAGE), 0.001);
        assertEquals(0.3, defaultConfig.scoring().ignoreConfidenceBelow(), 0.001);
        
        // 严重性惩罚
        assertEquals(1.0, defaultConfig.scoring().severityPenalty().get(Severity.INFO), 0.001);
        assertEquals(3.0, defaultConfig.scoring().severityPenalty().get(Severity.MINOR), 0.001);
        assertEquals(7.0, defaultConfig.scoring().severityPenalty().get(Severity.MAJOR), 0.001);
        assertEquals(12.0, defaultConfig.scoring().severityPenalty().get(Severity.CRITICAL), 0.001);
        
        // 报告配置
        assertNotNull(defaultConfig.report());
        assertNotNull(defaultConfig.report().export());
        assertTrue(defaultConfig.report().export().sarif());
        assertTrue(defaultConfig.report().export().json());
        assertTrue(defaultConfig.report().export().pdf());
        assertTrue(defaultConfig.report().export().html());
    }

    @Test
    void testValidateWeights_Success() {
        // Given
        Map<Dimension, Double> validWeights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            validWeights,
            Map.of(
                Severity.INFO, 1.0,
                Severity.MINOR, 3.0,
                Severity.MAJOR, 7.0,
                Severity.CRITICAL, 12.0
            ),
            0.3
        );

        // When & Then - 不应抛出异常
        assertDoesNotThrow(() -> scoringConfig.validateWeights());
    }

    @Test
    void testValidateWeights_InvalidSum() {
        // Given - 权重和不为1.0
        Map<Dimension, Double> invalidWeights = Map.of(
            Dimension.SECURITY, 0.40,
            Dimension.QUALITY, 0.30,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.10,
            Dimension.TEST_COVERAGE, 0.05  // 总和 = 1.05
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            invalidWeights,
            Map.of(
                Severity.INFO, 1.0,
                Severity.MINOR, 3.0,
                Severity.MAJOR, 7.0,
                Severity.CRITICAL, 12.0
            ),
            0.3
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> scoringConfig.validateWeights());
        assertTrue(exception.getMessage().contains("Weights must sum to 1.0"));
    }

    @Test
    void testValidateWeights_MissingDimension() {
        // Given - 缺少某个维度
        Map<Dimension, Double> incompleteWeights = Map.of(
            Dimension.SECURITY, 0.40,
            Dimension.QUALITY, 0.30,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.10
            // 缺少 TEST_COVERAGE
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            incompleteWeights,
            Map.of(
                Severity.INFO, 1.0,
                Severity.MINOR, 3.0,
                Severity.MAJOR, 7.0,
                Severity.CRITICAL, 12.0
            ),
            0.3
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> scoringConfig.validateWeights());
        assertTrue(exception.getMessage().contains("Missing weight for dimension"));
    }

    @Test
    void testValidateSeverityPenalty_Success() {
        // Given
        Map<Severity, Double> validPenalty = Map.of(
            Severity.INFO, 1.0,
            Severity.MINOR, 3.0,
            Severity.MAJOR, 7.0,
            Severity.CRITICAL, 12.0
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            Map.of(
                Dimension.SECURITY, 0.30,
                Dimension.QUALITY, 0.25,
                Dimension.MAINTAINABILITY, 0.20,
                Dimension.PERFORMANCE, 0.15,
                Dimension.TEST_COVERAGE, 0.10
            ),
            validPenalty,
            0.3
        );

        // When & Then - 不应抛出异常
        assertDoesNotThrow(() -> scoringConfig.validateSeverityPenalty());
    }

    @Test
    void testValidateSeverityPenalty_WrongOrder() {
        // Given - 惩罚值不是递增的
        Map<Severity, Double> invalidPenalty = Map.of(
            Severity.INFO, 10.0,   // 太高
            Severity.MINOR, 3.0,
            Severity.MAJOR, 7.0,
            Severity.CRITICAL, 12.0
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            Map.of(
                Dimension.SECURITY, 0.30,
                Dimension.QUALITY, 0.25,
                Dimension.MAINTAINABILITY, 0.20,
                Dimension.PERFORMANCE, 0.15,
                Dimension.TEST_COVERAGE, 0.10
            ),
            invalidPenalty,
            0.3
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> scoringConfig.validateSeverityPenalty());
        assertTrue(exception.getMessage().contains("Severity penalties must be in ascending order"));
    }

    @Test
    void testValidateSeverityPenalty_MissingSeverity() {
        // Given - 缺少某个严重性级别
        Map<Severity, Double> incompletePenalty = Map.of(
            Severity.INFO, 1.0,
            Severity.MINOR, 3.0,
            Severity.MAJOR, 7.0
            // 缺少 CRITICAL
        );
        
        AiReviewConfig.ScoringConfig scoringConfig = new AiReviewConfig.ScoringConfig(
            Map.of(
                Dimension.SECURITY, 0.30,
                Dimension.QUALITY, 0.25,
                Dimension.MAINTAINABILITY, 0.20,
                Dimension.PERFORMANCE, 0.15,
                Dimension.TEST_COVERAGE, 0.10
            ),
            incompletePenalty,
            0.3
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> scoringConfig.validateSeverityPenalty());
        assertTrue(exception.getMessage().contains("Missing penalty for severity"));
    }

    @Test
    void testValidateExportConfig_AllFormatsEnabled() {
        // Given
        AiReviewConfig.ReportConfig.ExportConfig exportConfig = 
            new AiReviewConfig.ReportConfig.ExportConfig(true, true, true, true);

        // When & Then - 不应抛出异常
        assertDoesNotThrow(() -> exportConfig.validateAtLeastOneFormat());
    }

    @Test
    void testValidateExportConfig_OneFormatEnabled() {
        // Given
        AiReviewConfig.ReportConfig.ExportConfig exportConfig = 
            new AiReviewConfig.ReportConfig.ExportConfig(false, true, false, false);

        // When & Then - 不应抛出异常
        assertDoesNotThrow(() -> exportConfig.validateAtLeastOneFormat());
    }

    @Test
    void testValidateExportConfig_NoFormatsEnabled() {
        // Given
        AiReviewConfig.ReportConfig.ExportConfig exportConfig = 
            new AiReviewConfig.ReportConfig.ExportConfig(false, false, false, false);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> exportConfig.validateAtLeastOneFormat());
        assertTrue(exception.getMessage().contains("At least one export format must be enabled"));
    }

    @Test
    void testFullConfigValidation() {
        // Given
        AiReviewConfig validConfig = AiReviewConfig.getDefault();

        // When & Then - 不应抛出异常
        assertDoesNotThrow(() -> validConfig.validate());
    }

    @Test
    void testLlmConfig() {
        // Given
        List<String> adapters = List.of("gpt-4o", "claude-3.5-sonnet");
        double budget = 1.25;
        
        // When
        AiReviewConfig.LlmConfig llmConfig = new AiReviewConfig.LlmConfig(adapters, budget);

        // Then
        assertEquals(2, llmConfig.adapters().size());
        assertTrue(llmConfig.adapters().contains("gpt-4o"));
        assertTrue(llmConfig.adapters().contains("claude-3.5-sonnet"));
        assertEquals(1.25, llmConfig.budgetUsd(), 0.001);
    }

    @Test
    void testCustomConfig() {
        // Given
        AiReviewConfig customConfig = new AiReviewConfig(
            "gitlab",
            new AiReviewConfig.LlmConfig(List.of("local-qwen"), 0.10),
            new AiReviewConfig.ScoringConfig(
                Map.of(
                    Dimension.SECURITY, 0.50,
                    Dimension.QUALITY, 0.20,
                    Dimension.MAINTAINABILITY, 0.15,
                    Dimension.PERFORMANCE, 0.10,
                    Dimension.TEST_COVERAGE, 0.05
                ),
                Map.of(
                    Severity.INFO, 0.5,
                    Severity.MINOR, 2.0,
                    Severity.MAJOR, 5.0,
                    Severity.CRITICAL, 10.0
                ),
                0.5
            ),
            new AiReviewConfig.ReportConfig(
                new AiReviewConfig.ReportConfig.ExportConfig(true, false, false, true)
            )
        );

        // When & Then
        assertEquals("gitlab", customConfig.provider());
        assertEquals(1, customConfig.llm().adapters().size());
        assertEquals("local-qwen", customConfig.llm().adapters().get(0));
        assertEquals(0.10, customConfig.llm().budgetUsd(), 0.001);
        assertEquals(0.50, customConfig.scoring().weights().get(Dimension.SECURITY), 0.001);
        assertEquals(0.5, customConfig.scoring().ignoreConfidenceBelow(), 0.001);
        assertTrue(customConfig.report().export().sarif());
        assertFalse(customConfig.report().export().json());
        assertFalse(customConfig.report().export().pdf());
        assertTrue(customConfig.report().export().html());
        
        // 验证配置有效性
        assertDoesNotThrow(() -> customConfig.validate());
    }
}
