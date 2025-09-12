package com.ai.reviewer.backend.domain.scoring;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.model.Scores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScoringService 单元测试。
 * 
 * <p>测试评分服务的各种场景，确保计算逻辑正确且结果可重复。
 */
class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    @Test
    @DisplayName("无发现时应返回满分")
    void testCalculateScores_NoFindings_ReturnsPerfectScore() {
        // Given
        List<Finding> findings = List.of();
        int linesChanged = 100;

        // When
        Scores scores = scoringService.calculateScores(findings, linesChanged);

        // Then
        assertEquals(100.0, scores.totalScore(), 0.01);
        scores.dimensions().values().forEach(score -> assertEquals(100.0, score, 0.01));
    }

    @Test
    @DisplayName("低置信度发现应被忽略")
    void testCalculateScores_LowConfidenceFindings_AreIgnored() {
        // Given
        List<Finding> findings = List.of(
            createFinding(Severity.CRITICAL, Dimension.SECURITY, 0.2), // 低置信度，应被忽略
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.5)      // 高置信度，应被计算
        );
        int linesChanged = 100;

        // When
        Scores scores = scoringService.calculateScores(findings, linesChanged);

        // Then
        // 安全维度分数应该是满分（因为低置信度发现被忽略）
        assertEquals(100.0, scores.dimensions().get(Dimension.SECURITY), 0.01);
        
        // 质量维度分数应该低于满分（因为有有效发现）
        assertTrue(scores.dimensions().get(Dimension.QUALITY) < 100.0);
    }

    @Test
    @DisplayName("不同严重性的惩罚值应不同")
    void testCalculateScores_DifferentSeverities_DifferentPenalties() {
        // Given
        int linesChanged = 100;
        
        // 相同置信度和维度，不同严重性的发现
        List<Finding> minorFindings = List.of(
            createFinding(Severity.MINOR, Dimension.QUALITY, 1.0)
        );
        
        List<Finding> criticalFindings = List.of(
            createFinding(Severity.CRITICAL, Dimension.QUALITY, 1.0)
        );

        // When
        Scores minorScores = scoringService.calculateScores(minorFindings, linesChanged);
        Scores criticalScores = scoringService.calculateScores(criticalFindings, linesChanged);

        // Then
        // CRITICAL 的惩罚应该大于 MINOR，因此分数更低
        assertTrue(criticalScores.dimensions().get(Dimension.QUALITY) < 
                   minorScores.dimensions().get(Dimension.QUALITY));
    }

    @Test
    @DisplayName("规模归一化应影响惩罚值")
    void testCalculateScores_ScaleNormalization_AffectsPenalty() {
        // Given
        List<Finding> findings = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 1.0)
        );
        
        int smallChange = 10;
        int largeChange = 1000;

        // When
        Scores smallChangeScores = scoringService.calculateScores(findings, smallChange);
        Scores largeChangeScores = scoringService.calculateScores(findings, largeChange);

        // Then
        // 较大的变更规模应该产生较大的惩罚，因此分数更低
        assertTrue(largeChangeScores.dimensions().get(Dimension.SECURITY) < 
                   smallChangeScores.dimensions().get(Dimension.SECURITY));
    }

    @Test
    @DisplayName("维度权重应正确影响总分")
    void testCalculateScores_DimensionWeights_AffectTotalScore() {
        // Given
        List<Finding> findings = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 1.0),
            createFinding(Severity.MAJOR, Dimension.QUALITY, 1.0)
        );
        int linesChanged = 100;

        // When
        Scores scores = scoringService.calculateScores(findings, linesChanged);

        // Then
        Map<Dimension, Double> weights = scoringService.getDefaultDimensionWeights();
        
        // 验证权重配置正确
        assertEquals(0.30, weights.get(Dimension.SECURITY), 0.01);
        assertEquals(0.25, weights.get(Dimension.QUALITY), 0.01);
        assertEquals(0.20, weights.get(Dimension.MAINTAINABILITY), 0.01);
        assertEquals(0.15, weights.get(Dimension.PERFORMANCE), 0.01);
        assertEquals(0.10, weights.get(Dimension.TEST_COVERAGE), 0.01);
        
        // 总权重应为1.0
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, totalWeight, 0.01);
    }

    @Test
    @DisplayName("多个发现应累积惩罚")
    void testCalculateScores_MultipleFindings_AccumulatePenalty() {
        // Given
        List<Finding> singleFinding = List.of(
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.8)
        );
        
        List<Finding> multipleFindings = List.of(
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.8),
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.8),
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.8)
        );
        
        int linesChanged = 100;

        // When
        Scores singleScore = scoringService.calculateScores(singleFinding, linesChanged);
        Scores multipleScore = scoringService.calculateScores(multipleFindings, linesChanged);

        // Then
        // 多个发现应该产生更低的分数
        assertTrue(multipleScore.dimensions().get(Dimension.QUALITY) < 
                   singleScore.dimensions().get(Dimension.QUALITY));
    }

    @Test
    @DisplayName("置信度应影响惩罚权重")
    void testCalculateScores_Confidence_AffectsPenalty() {
        // Given
        int linesChanged = 100;
        
        List<Finding> lowConfidence = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 0.4)
        );
        
        List<Finding> highConfidence = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 0.9)
        );

        // When
        Scores lowConfidenceScores = scoringService.calculateScores(lowConfidence, linesChanged);
        Scores highConfidenceScores = scoringService.calculateScores(highConfidence, linesChanged);

        // Then
        // 高置信度的发现应该产生更大的惩罚，因此分数更低
        assertTrue(highConfidenceScores.dimensions().get(Dimension.SECURITY) < 
                   lowConfidenceScores.dimensions().get(Dimension.SECURITY));
    }

    @Test
    @DisplayName("计算结果应该可重复")
    void testCalculateScores_Repeatability() {
        // Given
        List<Finding> findings = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 0.8),
            createFinding(Severity.MINOR, Dimension.QUALITY, 0.7),
            createFinding(Severity.CRITICAL, Dimension.MAINTAINABILITY, 0.9)
        );
        int linesChanged = 150;

        // When
        Scores scores1 = scoringService.calculateScores(findings, linesChanged);
        Scores scores2 = scoringService.calculateScores(findings, linesChanged);

        // Then
        assertEquals(scores1.totalScore(), scores2.totalScore(), 0.01);
        
        for (Dimension dimension : Dimension.values()) {
            assertEquals(scores1.dimensions().get(dimension), 
                        scores2.dimensions().get(dimension), 0.01);
        }
    }

    @Test
    @DisplayName("严重性惩罚配置应正确")
    void testSeverityPenaltyConfiguration() {
        // When
        Map<Severity, Integer> penalties = scoringService.getSeverityPenalty();

        // Then
        assertEquals(1, penalties.get(Severity.INFO));
        assertEquals(3, penalties.get(Severity.MINOR));
        assertEquals(7, penalties.get(Severity.MAJOR));
        assertEquals(12, penalties.get(Severity.CRITICAL));
    }

    @Test
    @DisplayName("置信度阈值应正确")
    void testConfidenceThreshold() {
        // When
        double threshold = scoringService.getIgnoreConfidenceBelow();

        // Then
        assertEquals(0.3, threshold, 0.01);
    }

    @Test
    @DisplayName("边界情况：零变更行数")
    void testCalculateScores_ZeroLinesChanged() {
        // Given
        List<Finding> findings = List.of(
            createFinding(Severity.MAJOR, Dimension.SECURITY, 0.8)
        );
        int linesChanged = 0;

        // When & Then - 应该不抛出异常
        assertDoesNotThrow(() -> {
            Scores scores = scoringService.calculateScores(findings, linesChanged);
            assertNotNull(scores);
            assertTrue(scores.totalScore() >= 0.0);
            assertTrue(scores.totalScore() <= 100.0);
        });
    }

    /**
     * 创建测试用的 Finding 对象。
     */
    private Finding createFinding(Severity severity, Dimension dimension, double confidence) {
        return new Finding(
            "test-id-" + System.nanoTime(),
            "TestFile.java",
            10,
            12,
            severity,
            dimension,
            "Test finding",
            "test evidence",
            "test suggestion",
            null,
            List.of("test-analyzer"),
            confidence
        );
    }
}
