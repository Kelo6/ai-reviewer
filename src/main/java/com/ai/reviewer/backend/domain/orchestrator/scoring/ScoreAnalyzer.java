package com.ai.reviewer.backend.domain.orchestrator.scoring;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.model.Scores;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评分分析器，提供深度的评分解读和趋势分析。
 * 
 * <p>功能包括：
 * - 评分详细解读和建议
 * - 历史趋势分析
 * - 维度对比分析
 * - 改进潜力评估
 * - 评分等级划分
 * - 基准对比分析
 */
@Component
public class ScoreAnalyzer {
    
    /**
     * 评分等级阈值配置
     */
    private static final Map<ScoreGrade, ScoreRange> SCORE_RANGES = Map.of(
        ScoreGrade.EXCELLENT, new ScoreRange(90.0, 100.0),
        ScoreGrade.GOOD, new ScoreRange(75.0, 89.9),
        ScoreGrade.FAIR, new ScoreRange(60.0, 74.9),
        ScoreGrade.POOR, new ScoreRange(40.0, 59.9),
        ScoreGrade.CRITICAL, new ScoreRange(0.0, 39.9)
    );
    
    /**
     * 行业基准分数（可配置）
     */
    private static final Map<Dimension, Double> INDUSTRY_BENCHMARKS = Map.of(
        Dimension.SECURITY, 85.0,
        Dimension.QUALITY, 78.0,
        Dimension.MAINTAINABILITY, 72.0,
        Dimension.PERFORMANCE, 75.0,
        Dimension.TEST_COVERAGE, 65.0
    );
    
    /**
     * 生成完整的评分分析报告。
     * 
     * @param scores 评分结果
     * @param findings 发现列表
     * @param historicalScores 历史评分（可选）
     * @return 评分分析报告
     */
    public ScoreAnalysisReport generateAnalysisReport(Scores scores, List<Finding> findings, 
                                                    List<HistoricalScore> historicalScores) {
        // 基础评分分析
        ScoreGradeAnalysis gradeAnalysis = analyzeGrade(scores);
        
        // 维度分析
        List<DimensionAnalysis> dimensionAnalyses = analyzeDimensions(scores, findings);
        
        // 趋势分析
        TrendAnalysis trendAnalysis = analyzeTrends(scores, historicalScores);
        
        // 基准对比
        BenchmarkComparison benchmarkComparison = compareToBenchmarks(scores);
        
        // 改进建议
        List<ImprovementRecommendation> recommendations = generateRecommendations(scores, findings, dimensionAnalyses);
        
        // 风险评估
        RiskAssessment riskAssessment = assessRisks(scores, findings);
        
        return new ScoreAnalysisReport(
            scores,
            gradeAnalysis,
            dimensionAnalyses,
            trendAnalysis,
            benchmarkComparison,
            recommendations,
            riskAssessment,
            Instant.now()
        );
    }
    
    /**
     * 分析评分等级。
     */
    private ScoreGradeAnalysis analyzeGrade(Scores scores) {
        ScoreGrade grade = determineGrade(scores.totalScore());
        ScoreRange range = SCORE_RANGES.get(grade);
        
        double progressInGrade = (scores.totalScore() - range.min()) / (range.max() - range.min());
        
        String interpretation = generateGradeInterpretation(grade, scores.totalScore());
        String nextGoal = generateNextGoal(grade, scores.totalScore());
        
        return new ScoreGradeAnalysis(
            grade,
            scores.totalScore(),
            range,
            progressInGrade,
            interpretation,
            nextGoal
        );
    }
    
    /**
     * 分析各维度得分。
     */
    private List<DimensionAnalysis> analyzeDimensions(Scores scores, List<Finding> findings) {
        List<DimensionAnalysis> analyses = new ArrayList<>();
        
        Map<Dimension, List<Finding>> findingsByDimension = findings.stream()
            .collect(Collectors.groupingBy(Finding::dimension));
        
        for (Dimension dimension : Dimension.values()) {
            Double score = scores.dimensions().get(dimension);
            if (score == null) continue;
            
            List<Finding> dimensionFindings = findingsByDimension.getOrDefault(dimension, List.of());
            
            DimensionAnalysis analysis = analyzeDimension(dimension, score, dimensionFindings, scores.weights());
            analyses.add(analysis);
        }
        
        // 按重要性排序
        analyses.sort((a, b) -> Double.compare(b.weightedImpact(), a.weightedImpact()));
        
        return analyses;
    }
    
    /**
     * 分析单个维度。
     */
    private DimensionAnalysis analyzeDimension(Dimension dimension, double score, 
                                             List<Finding> findings, Map<Dimension, Double> weights) {
        ScoreGrade grade = determineGrade(score);
        Double weight = weights.getOrDefault(dimension, 0.0);
        double weightedImpact = score * weight;
        
        // 问题统计
        Map<Severity, Long> severityStats = findings.stream()
            .collect(Collectors.groupingBy(Finding::severity, Collectors.counting()));
        
        // 置信度分析
        double avgConfidence = findings.stream()
            .mapToDouble(Finding::confidence)
            .average()
            .orElse(1.0);
        
        // 改进潜力
        double improvementPotential = calculateImprovementPotential(dimension, score, findings);
        
        // 关键问题
        List<Finding> criticalIssues = findings.stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .limit(3)
            .toList();
        
        // 基准对比
        Double benchmark = INDUSTRY_BENCHMARKS.get(dimension);
        String benchmarkStatus = benchmark != null ? 
            (score >= benchmark ? "高于行业平均" : "低于行业平均") : "无基准数据";
        
        String assessment = generateDimensionAssessment(dimension, grade, findings.size());
        List<String> suggestions = generateDimensionSuggestions(dimension, findings);
        
        return new DimensionAnalysis(
            dimension,
            score,
            grade,
            weight,
            weightedImpact,
            severityStats,
            avgConfidence,
            improvementPotential,
            criticalIssues,
            benchmarkStatus,
            assessment,
            suggestions
        );
    }
    
    /**
     * 分析评分趋势。
     */
    private TrendAnalysis analyzeTrends(Scores currentScores, List<HistoricalScore> historicalScores) {
        if (historicalScores == null || historicalScores.isEmpty()) {
            return new TrendAnalysis(
                TrendDirection.UNKNOWN,
                0.0,
                Map.of(),
                List.of(),
                "缺乏历史数据进行趋势分析"
            );
        }
        
        // 计算总分趋势
        List<Double> totalScores = historicalScores.stream()
            .map(HistoricalScore::totalScore)
            .toList();
        totalScores = new ArrayList<>(totalScores);
        totalScores.add(currentScores.totalScore());
        
        TrendDirection overallTrend = calculateTrendDirection(totalScores);
        double trendStrength = calculateTrendStrength(totalScores);
        
        // 计算各维度趋势
        Map<Dimension, TrendDirection> dimensionTrends = new HashMap<>();
        for (Dimension dimension : Dimension.values()) {
            List<Double> dimensionScores = historicalScores.stream()
                .map(hs -> hs.dimensionScores().getOrDefault(dimension, 0.0))
                .collect(Collectors.toList());
            dimensionScores.add(currentScores.dimensions().getOrDefault(dimension, 0.0));
            
            dimensionTrends.put(dimension, calculateTrendDirection(dimensionScores));
        }
        
        // 生成趋势洞察
        List<String> insights = generateTrendInsights(overallTrend, trendStrength, dimensionTrends);
        
        String interpretation = generateTrendInterpretation(overallTrend, trendStrength);
        
        return new TrendAnalysis(
            overallTrend,
            trendStrength,
            dimensionTrends,
            insights,
            interpretation
        );
    }
    
    /**
     * 基准对比分析。
     */
    private BenchmarkComparison compareToBenchmarks(Scores scores) {
        Map<Dimension, BenchmarkResult> results = new HashMap<>();
        
        for (Dimension dimension : Dimension.values()) {
            Double score = scores.dimensions().get(dimension);
            Double benchmark = INDUSTRY_BENCHMARKS.get(dimension);
            
            if (score != null && benchmark != null) {
                double difference = score - benchmark;
                double percentageDiff = (difference / benchmark) * 100;
                
                BenchmarkStatus status = difference >= 0 ? 
                    (difference >= 10 ? BenchmarkStatus.SIGNIFICANTLY_ABOVE : BenchmarkStatus.ABOVE) :
                    (difference <= -10 ? BenchmarkStatus.SIGNIFICANTLY_BELOW : BenchmarkStatus.BELOW);
                
                results.put(dimension, new BenchmarkResult(
                    score, benchmark, difference, percentageDiff, status
                ));
            }
        }
        
        // 整体评估
        double avgPerformance = results.values().stream()
            .mapToDouble(BenchmarkResult::percentageDifference)
            .average()
            .orElse(0.0);
        
        String overallAssessment = generateBenchmarkAssessment(avgPerformance);
        List<String> recommendations = generateBenchmarkRecommendations(results);
        
        return new BenchmarkComparison(
            results,
            avgPerformance,
            overallAssessment,
            recommendations
        );
    }
    
    /**
     * 生成改进建议。
     */
    private List<ImprovementRecommendation> generateRecommendations(Scores scores, List<Finding> findings,
                                                                  List<DimensionAnalysis> dimensionAnalyses) {
        List<ImprovementRecommendation> recommendations = new ArrayList<>();
        
        // 基于最低分维度的建议
        dimensionAnalyses.stream()
            .filter(analysis -> analysis.grade() == ScoreGrade.POOR || analysis.grade() == ScoreGrade.CRITICAL)
            .limit(3)
            .forEach(analysis -> {
                ImprovementRecommendation recommendation = createDimensionRecommendation(analysis);
                recommendations.add(recommendation);
            });
        
        // 基于严重问题的建议
        findings.stream()
            .filter(f -> f.severity() == Severity.CRITICAL)
            .limit(5)
            .forEach(finding -> {
                ImprovementRecommendation recommendation = createFindingRecommendation(finding);
                recommendations.add(recommendation);
            });
        
        // 通用改进建议
        if (scores.totalScore() < 60) {
            recommendations.add(new ImprovementRecommendation(
                RecommendationType.URGENT,
                "整体代码质量改进",
                "当前代码质量得分较低，建议进行全面的代码审查和重构",
                List.of("进行代码审查", "制定改进计划", "设置质量门禁"),
                30, // 预估改进天数
                15.0 // 预估分数提升
            ));
        }
        
        return recommendations;
    }
    
    /**
     * 风险评估。
     */
    private RiskAssessment assessRisks(Scores scores, List<Finding> findings) {
        // 计算各种风险
        SecurityRisk securityRisk = assessSecurityRisk(scores, findings);
        QualityRisk qualityRisk = assessQualityRisk(scores, findings);
        MaintenanceRisk maintenanceRisk = assessMaintenanceRisk(scores, findings);
        
        // 整体风险等级
        RiskLevel overallRisk = determineOverallRisk(securityRisk, qualityRisk, maintenanceRisk);
        
        // 风险缓解建议
        List<String> mitigationStrategies = generateMitigationStrategies(overallRisk, findings);
        
        return new RiskAssessment(
            overallRisk,
            securityRisk,
            qualityRisk,
            maintenanceRisk,
            mitigationStrategies
        );
    }
    
    // 辅助方法实现
    private ScoreGrade determineGrade(double score) {
        for (Map.Entry<ScoreGrade, ScoreRange> entry : SCORE_RANGES.entrySet()) {
            ScoreRange range = entry.getValue();
            if (score >= range.min() && score <= range.max()) {
                return entry.getKey();
            }
        }
        return ScoreGrade.CRITICAL;
    }
    
    private String generateGradeInterpretation(ScoreGrade grade, double score) {
        return switch (grade) {
            case EXCELLENT -> String.format("优秀的代码质量（%.1f分），展现了高水平的工程实践", score);
            case GOOD -> String.format("良好的代码质量（%.1f分），符合行业标准要求", score);
            case FAIR -> String.format("一般的代码质量（%.1f分），有改进空间", score);
            case POOR -> String.format("较差的代码质量（%.1f分），需要重点改进", score);
            case CRITICAL -> String.format("严重的代码质量问题（%.1f分），需要立即处理", score);
        };
    }
    
    private String generateNextGoal(ScoreGrade grade, double score) {
        return switch (grade) {
            case EXCELLENT -> "保持当前高水平，关注新兴技术和最佳实践";
            case GOOD -> String.format("提升至优秀等级，目标分数：%.1f", 90.0);
            case FAIR -> String.format("提升至良好等级，目标分数：%.1f", 75.0);
            case POOR -> String.format("提升至一般等级，目标分数：%.1f", 60.0);
            case CRITICAL -> String.format("提升至较差等级，目标分数：%.1f", 40.0);
        };
    }
    
    private double calculateImprovementPotential(Dimension dimension, double score, List<Finding> findings) {
        // 基于发现的问题数量和严重性计算改进潜力
        double potentialFromFindings = findings.stream()
            .filter(f -> f.confidence() > 0.7)
            .mapToDouble(f -> switch (f.severity()) {
                case CRITICAL -> 8.0;
                case MAJOR -> 5.0;
                case MINOR -> 2.0;
                case INFO -> 1.0;
            })
            .sum();
        
        return Math.min(100.0 - score, potentialFromFindings);
    }
    
    private String generateDimensionAssessment(Dimension dimension, ScoreGrade grade, int issueCount) {
        String dimensionName = switch (dimension) {
            case SECURITY -> "安全性";
            case QUALITY -> "代码质量";
            case MAINTAINABILITY -> "可维护性";
            case PERFORMANCE -> "性能";
            case TEST_COVERAGE -> "测试覆盖率";
        };
        
        return String.format("%s评估：%s等级，发现%d个问题", 
            dimensionName, grade.getDisplayName(), issueCount);
    }
    
    private List<String> generateDimensionSuggestions(Dimension dimension, List<Finding> findings) {
        List<String> suggestions = new ArrayList<>();
        
        Map<Severity, Long> severityCount = findings.stream()
            .collect(Collectors.groupingBy(Finding::severity, Collectors.counting()));
        
        if (severityCount.getOrDefault(Severity.CRITICAL, 0L) > 0) {
            suggestions.add("立即处理严重问题，避免生产环境风险");
        }
        
        suggestions.add(switch (dimension) {
            case SECURITY -> "加强安全代码审查，使用安全编码规范";
            case QUALITY -> "提高代码复用性，减少重复代码";
            case MAINTAINABILITY -> "改善代码结构，增加文档注释";
            case PERFORMANCE -> "优化算法复杂度，减少资源消耗";
            case TEST_COVERAGE -> "增加单元测试，提高测试覆盖率";
        });
        
        return suggestions;
    }
    
    private TrendDirection calculateTrendDirection(List<Double> scores) {
        if (scores.size() < 2) return TrendDirection.UNKNOWN;
        
        double firstHalf = scores.subList(0, scores.size() / 2).stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        double secondHalf = scores.subList(scores.size() / 2, scores.size()).stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double difference = secondHalf - firstHalf;
        
        if (Math.abs(difference) < 2.0) return TrendDirection.STABLE;
        return difference > 0 ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
    }
    
    private double calculateTrendStrength(List<Double> scores) {
        if (scores.size() < 2) return 0.0;
        
        double totalChange = Math.abs(scores.get(scores.size() - 1) - scores.get(0));
        return Math.min(totalChange / 10.0, 1.0); // 归一化到0-1
    }
    
    private List<String> generateTrendInsights(TrendDirection overall, double strength, 
                                             Map<Dimension, TrendDirection> dimensionTrends) {
        List<String> insights = new ArrayList<>();
        
        insights.add(switch (overall) {
            case IMPROVING -> "代码质量呈现上升趋势，团队改进效果显著";
            case DECLINING -> "代码质量有下降趋势，需要加强质量管控";
            case STABLE -> "代码质量保持稳定，可考虑进一步提升";
            case UNKNOWN -> "缺乏足够历史数据进行趋势分析";
        });
        
        if (strength > 0.7) {
            insights.add("变化趋势明显，建议持续关注");
        }
        
        return insights;
    }
    
    private String generateTrendInterpretation(TrendDirection trend, double strength) {
        return String.format("总体趋势：%s，变化强度：%.1f", 
            trend.getDisplayName(), strength * 100);
    }
    
    private String generateBenchmarkAssessment(double avgPerformance) {
        if (avgPerformance >= 10) {
            return "显著高于行业平均水平";
        } else if (avgPerformance >= 0) {
            return "略高于行业平均水平";
        } else if (avgPerformance >= -10) {
            return "略低于行业平均水平";
        } else {
            return "显著低于行业平均水平";
        }
    }
    
    private List<String> generateBenchmarkRecommendations(Map<Dimension, BenchmarkResult> results) {
        List<String> recommendations = new ArrayList<>();
        
        results.entrySet().stream()
            .filter(entry -> entry.getValue().status() == BenchmarkStatus.SIGNIFICANTLY_BELOW)
            .forEach(entry -> {
                String dimensionName = switch (entry.getKey()) {
                    case SECURITY -> "安全性";
                    case QUALITY -> "代码质量";
                    case MAINTAINABILITY -> "可维护性";
                    case PERFORMANCE -> "性能";
                    case TEST_COVERAGE -> "测试覆盖率";
                };
                recommendations.add(String.format("重点改进%s，当前远低于行业标准", dimensionName));
            });
        
        return recommendations;
    }
    
    private ImprovementRecommendation createDimensionRecommendation(DimensionAnalysis analysis) {
        String title = String.format("改进%s", analysis.dimension().name());
        String description = String.format("当前%s得分为%.1f，建议重点关注", 
            analysis.dimension().name(), analysis.score());
        
        List<String> actions = analysis.suggestions();
        int estimatedDays = (int) (analysis.improvementPotential() * 2); // 简单估算
        
        return new ImprovementRecommendation(
            RecommendationType.HIGH,
            title,
            description,
            actions,
            estimatedDays,
            analysis.improvementPotential()
        );
    }
    
    private ImprovementRecommendation createFindingRecommendation(Finding finding) {
        return new ImprovementRecommendation(
            RecommendationType.URGENT,
            "修复严重问题：" + finding.title(),
            finding.suggestion(),
            List.of("立即修复", "增加测试", "代码审查"),
            3, // 3天内修复
            5.0 // 预估提升5分
        );
    }
    
    private SecurityRisk assessSecurityRisk(Scores scores, List<Finding> findings) {
        long securityIssues = findings.stream()
            .filter(f -> f.dimension() == Dimension.SECURITY)
            .count();
        
        Double securityScore = scores.dimensions().get(Dimension.SECURITY);
        
        RiskLevel level = RiskLevel.LOW;
        if (securityScore != null && securityScore < 50 && securityIssues > 5) {
            level = RiskLevel.HIGH;
        } else if (securityScore != null && securityScore < 70 && securityIssues > 2) {
            level = RiskLevel.MEDIUM;
        }
        
        return new SecurityRisk(level, securityIssues, securityScore);
    }
    
    private QualityRisk assessQualityRisk(Scores scores, List<Finding> findings) {
        Double qualityScore = scores.dimensions().get(Dimension.QUALITY);
        long qualityIssues = findings.stream()
            .filter(f -> f.dimension() == Dimension.QUALITY)
            .count();
        
        RiskLevel level = RiskLevel.LOW;
        if (qualityScore != null && qualityScore < 60) {
            level = RiskLevel.MEDIUM;
        }
        if (qualityScore != null && qualityScore < 40) {
            level = RiskLevel.HIGH;
        }
        
        return new QualityRisk(level, qualityIssues, qualityScore);
    }
    
    private MaintenanceRisk assessMaintenanceRisk(Scores scores, List<Finding> findings) {
        Double maintainabilityScore = scores.dimensions().get(Dimension.MAINTAINABILITY);
        long maintainabilityIssues = findings.stream()
            .filter(f -> f.dimension() == Dimension.MAINTAINABILITY)
            .count();
        
        RiskLevel level = RiskLevel.LOW;
        if (maintainabilityScore != null && maintainabilityScore < 50) {
            level = RiskLevel.MEDIUM;
        }
        
        return new MaintenanceRisk(level, maintainabilityIssues, maintainabilityScore);
    }
    
    private RiskLevel determineOverallRisk(SecurityRisk security, QualityRisk quality, MaintenanceRisk maintenance) {
        if (security.level() == RiskLevel.HIGH || quality.level() == RiskLevel.HIGH) {
            return RiskLevel.HIGH;
        }
        if (security.level() == RiskLevel.MEDIUM || quality.level() == RiskLevel.MEDIUM || 
            maintenance.level() == RiskLevel.MEDIUM) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
    
    private List<String> generateMitigationStrategies(RiskLevel overallRisk, List<Finding> findings) {
        List<String> strategies = new ArrayList<>();
        
        switch (overallRisk) {
            case HIGH -> {
                strategies.add("立即启动代码质量改进计划");
                strategies.add("增加代码审查频率");
                strategies.add("暂停新功能开发，专注质量修复");
            }
            case MEDIUM -> {
                strategies.add("制定渐进式改进计划");
                strategies.add("加强团队培训");
                strategies.add("引入自动化质量检查");
            }
            case LOW -> {
                strategies.add("保持当前质量水平");
                strategies.add("定期进行质量回顾");
            }
        }
        
        return strategies;
    }
    
    // 数据类定义
    public record ScoreRange(double min, double max) {}
    
    public record HistoricalScore(
        Instant timestamp,
        double totalScore,
        Map<Dimension, Double> dimensionScores
    ) {}
    
    public record ScoreAnalysisReport(
        Scores scores,
        ScoreGradeAnalysis gradeAnalysis,
        List<DimensionAnalysis> dimensionAnalyses,
        TrendAnalysis trendAnalysis,
        BenchmarkComparison benchmarkComparison,
        List<ImprovementRecommendation> recommendations,
        RiskAssessment riskAssessment,
        Instant analyzedAt
    ) {}
    
    public record ScoreGradeAnalysis(
        ScoreGrade grade,
        double score,
        ScoreRange range,
        double progressInGrade,
        String interpretation,
        String nextGoal
    ) {}
    
    public record DimensionAnalysis(
        Dimension dimension,
        double score,
        ScoreGrade grade,
        double weight,
        double weightedImpact,
        Map<Severity, Long> severityStats,
        double avgConfidence,
        double improvementPotential,
        List<Finding> criticalIssues,
        String benchmarkStatus,
        String assessment,
        List<String> suggestions
    ) {}
    
    public record TrendAnalysis(
        TrendDirection overallTrend,
        double trendStrength,
        Map<Dimension, TrendDirection> dimensionTrends,
        List<String> insights,
        String interpretation
    ) {}
    
    public record BenchmarkComparison(
        Map<Dimension, BenchmarkResult> results,
        double avgPerformance,
        String overallAssessment,
        List<String> recommendations
    ) {}
    
    public record BenchmarkResult(
        double score,
        double benchmark,
        double difference,
        double percentageDifference,
        BenchmarkStatus status
    ) {}
    
    public record ImprovementRecommendation(
        RecommendationType type,
        String title,
        String description,
        List<String> actionItems,
        int estimatedDays,
        double estimatedScoreImprovement
    ) {}
    
    public record RiskAssessment(
        RiskLevel overallRisk,
        SecurityRisk securityRisk,
        QualityRisk qualityRisk,
        MaintenanceRisk maintenanceRisk,
        List<String> mitigationStrategies
    ) {}
    
    public record SecurityRisk(RiskLevel level, long issueCount, Double score) {}
    public record QualityRisk(RiskLevel level, long issueCount, Double score) {}
    public record MaintenanceRisk(RiskLevel level, long issueCount, Double score) {}
    
    // 枚举定义
    public enum ScoreGrade {
        EXCELLENT("优秀", "#10b981"),
        GOOD("良好", "#3b82f6"),
        FAIR("一般", "#f59e0b"),
        POOR("较差", "#ef4444"),
        CRITICAL("严重", "#dc2626");
        
        private final String displayName;
        private final String color;
        
        ScoreGrade(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    public enum TrendDirection {
        IMPROVING("改善"),
        DECLINING("下降"),
        STABLE("稳定"),
        UNKNOWN("未知");
        
        private final String displayName;
        
        TrendDirection(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    public enum BenchmarkStatus {
        SIGNIFICANTLY_ABOVE("显著高于"),
        ABOVE("高于"),
        BELOW("低于"),
        SIGNIFICANTLY_BELOW("显著低于");
        
        private final String displayName;
        
        BenchmarkStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    public enum RecommendationType {
        URGENT("紧急"),
        HIGH("高"),
        MEDIUM("中"),
        LOW("低");
        
        private final String displayName;
        
        RecommendationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    public enum RiskLevel {
        HIGH("高风险", "#dc2626"),
        MEDIUM("中风险", "#f59e0b"),
        LOW("低风险", "#10b981");
        
        private final String displayName;
        private final String color;
        
        RiskLevel(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
}
