package com.ai.reviewer.backend.domain.scoring;

import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.model.Scores;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码质量评分服务。
 * 
 * <p>基于发现的问题和变更规模，计算各维度质量分数和总分。
 * 使用惩罚机制和规模归一化来确保评分的公平性和一致性。
 */
@Service
public class ScoringService {

    /**
     * 默认维度权重配置
     */
    private static final Map<Dimension, Double> DEFAULT_DIMENSION_WEIGHTS = Map.of(
        Dimension.SECURITY, 0.30,
        Dimension.QUALITY, 0.25,
        Dimension.MAINTAINABILITY, 0.20,
        Dimension.PERFORMANCE, 0.15,
        Dimension.TEST_COVERAGE, 0.10
    );

    /**
     * 严重性惩罚系数
     */
    private static final Map<Severity, Integer> SEVERITY_PENALTY = Map.of(
        Severity.INFO, 1,
        Severity.MINOR, 3,
        Severity.MAJOR, 7,
        Severity.CRITICAL, 12
    );

    /**
     * 忽略置信度阈值
     */
    private static final double IGNORE_CONFIDENCE_BELOW = 0.3;

    /**
     * 基础分数（满分）
     */
    private static final double BASE_SCORE = 100.0;

    /**
     * 计算代码质量分数。
     * 
     * @param findings 发现的问题列表
     * @param linesChanged 变更的代码行数
     * @return 计算后的分数结果
     */
    public Scores calculateScores(List<Finding> findings, int linesChanged) {
        return calculateScores(findings, linesChanged, DEFAULT_DIMENSION_WEIGHTS);
    }

    /**
     * 使用配置计算代码质量分数。
     * 
     * @param findings 发现的问题列表
     * @param linesChanged 变更的代码行数
     * @param scoringConfig 评分配置
     * @return 计算后的分数结果
     */
    public Scores calculateScores(List<Finding> findings, int linesChanged, AiReviewConfig.ScoringConfig scoringConfig) {
        if (scoringConfig == null) {
            return calculateScores(findings, linesChanged);
        }
        
        // 转换严重性惩罚配置从Double到Integer（保持兼容性）
        Map<Severity, Integer> severityPenalty = new HashMap<>();
        for (Map.Entry<Severity, Double> entry : scoringConfig.severityPenalty().entrySet()) {
            severityPenalty.put(entry.getKey(), entry.getValue().intValue());
        }
        
        return calculateScoresWithConfig(
            findings, 
            linesChanged, 
            scoringConfig.weights(), 
            severityPenalty,
            scoringConfig.ignoreConfidenceBelow()
        );
    }

    /**
     * 计算代码质量分数（可指定权重）。
     * 
     * @param findings 发现的问题列表
     * @param linesChanged 变更的代码行数
     * @param dimensionWeights 维度权重配置
     * @return 计算后的分数结果
     */
    public Scores calculateScores(List<Finding> findings, int linesChanged, Map<Dimension, Double> dimensionWeights) {
        return calculateScoresWithConfig(findings, linesChanged, dimensionWeights, SEVERITY_PENALTY, IGNORE_CONFIDENCE_BELOW);
    }

    /**
     * 使用完整配置计算代码质量分数。
     * 
     * @param findings 发现的问题列表
     * @param linesChanged 变更的代码行数
     * @param dimensionWeights 维度权重配置
     * @param severityPenalty 严重性惩罚配置
     * @param ignoreConfidenceBelow 置信度阈值
     * @return 计算后的分数结果
     */
    private Scores calculateScoresWithConfig(List<Finding> findings, 
                                          int linesChanged, 
                                          Map<Dimension, Double> dimensionWeights,
                                          Map<Severity, Integer> severityPenalty,
                                          double ignoreConfidenceBelow) {
        // 过滤低置信度的发现
        List<Finding> validFindings = findings.stream()
            .filter(finding -> finding.confidence() >= ignoreConfidenceBelow)
            .toList();

        // 按维度分组计算惩罚分数
        Map<Dimension, Double> dimensionScores = new HashMap<>();
        
        for (Dimension dimension : Dimension.values()) {
            double dimensionScore = calculateDimensionScore(validFindings, dimension, linesChanged, severityPenalty);
            dimensionScores.put(dimension, dimensionScore);
        }

        // 计算加权总分
        double totalScore = calculateWeightedTotalScore(dimensionScores, dimensionWeights);

        return new Scores(totalScore, dimensionScores, dimensionWeights);
    }

    /**
     * 计算单个维度的分数。
     * 
     * @param findings 所有有效发现
     * @param dimension 目标维度
     * @param linesChanged 变更行数
     * @return 该维度的分数
     */
    private double calculateDimensionScore(List<Finding> findings, Dimension dimension, int linesChanged) {
        return calculateDimensionScore(findings, dimension, linesChanged, SEVERITY_PENALTY);
    }

    /**
     * 计算单个维度的分数（使用指定的严重性惩罚配置）。
     * 
     * @param findings 所有有效发现
     * @param dimension 目标维度
     * @param linesChanged 变更行数
     * @param severityPenalty 严重性惩罚配置
     * @return 该维度的分数
     */
    private double calculateDimensionScore(List<Finding> findings, Dimension dimension, int linesChanged, 
                                         Map<Severity, Integer> severityPenalty) {
        // 计算该维度的总惩罚值
        double totalPenalty = findings.stream()
            .filter(finding -> finding.dimension() == dimension)
            .mapToDouble(finding -> calculateNormalizedPenalty(finding, linesChanged, severityPenalty))
            .sum();

        // 将惩罚转换为分数（0-100）
        // 使用指数衰减函数，确保分数在合理范围内
        double score = BASE_SCORE * Math.exp(-totalPenalty / 10.0);
        
        // 确保分数不低于0
        return Math.max(0.0, Math.min(BASE_SCORE, score));
    }

    /**
     * 计算经规模归一化的惩罚值。
     * 
     * @param finding 发现的问题
     * @param linesChanged 变更行数
     * @return 归一化后的惩罚值
     */
    private double calculateNormalizedPenalty(Finding finding, int linesChanged) {
        return calculateNormalizedPenalty(finding, linesChanged, SEVERITY_PENALTY);
    }

    /**
     * 计算经规模归一化的惩罚值（使用指定的严重性惩罚配置）。
     * 
     * @param finding 发现的问题
     * @param linesChanged 变更行数
     * @param severityPenalty 严重性惩罚配置
     * @return 归一化后的惩罚值
     */
    private double calculateNormalizedPenalty(Finding finding, int linesChanged, Map<Severity, Integer> severityPenalty) {
        // 基础惩罚值
        int basePenalty = severityPenalty.get(finding.severity());
        
        // 应用置信度权重
        double confidenceWeightedPenalty = basePenalty * finding.confidence();
        
        // 规模归一化：penalty *= log1p(linesChanged)/6
        double scaleFactor = Math.log1p(linesChanged) / 6.0;
        
        return confidenceWeightedPenalty * scaleFactor;
    }

    /**
     * 计算加权总分。
     * 
     * @param dimensionScores 各维度分数
     * @param dimensionWeights 维度权重
     * @return 加权总分
     */
    private double calculateWeightedTotalScore(Map<Dimension, Double> dimensionScores, 
                                             Map<Dimension, Double> dimensionWeights) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Dimension dimension : Dimension.values()) {
            double score = dimensionScores.getOrDefault(dimension, BASE_SCORE);
            double weight = dimensionWeights.getOrDefault(dimension, 0.0);
            
            weightedSum += score * weight;
            totalWeight += weight;
        }

        // 如果权重总和不为1，则进行归一化
        return totalWeight > 0 ? weightedSum / totalWeight : BASE_SCORE;
    }

    /**
     * 获取默认维度权重配置。
     * 
     * @return 默认维度权重映射
     */
    public Map<Dimension, Double> getDefaultDimensionWeights() {
        return new HashMap<>(DEFAULT_DIMENSION_WEIGHTS);
    }

    /**
     * 获取严重性惩罚配置。
     * 
     * @return 严重性惩罚映射
     */
    public Map<Severity, Integer> getSeverityPenalty() {
        return new HashMap<>(SEVERITY_PENALTY);
    }

    /**
     * 获取置信度阈值。
     * 
     * @return 置信度阈值
     */
    public double getIgnoreConfidenceBelow() {
        return IGNORE_CONFIDENCE_BELOW;
    }
}
