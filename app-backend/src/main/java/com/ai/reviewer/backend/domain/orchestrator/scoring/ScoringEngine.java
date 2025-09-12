package com.ai.reviewer.backend.domain.orchestrator.scoring;

import com.ai.reviewer.shared.model.*;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scoring engine that calculates quality scores based on findings.
 * 
 * <p>This component maps findings to dimensions, calculates dimension-specific
 * scores, and produces an overall quality score with configurable weightings.
 */
@Component
public class ScoringEngine {
    
    // Default severity impact on scores (0.0 = no impact, 1.0 = maximum impact)
    private static final Map<Severity, Double> DEFAULT_SEVERITY_IMPACT = Map.of(
        Severity.INFO, 0.1,
        Severity.MINOR, 0.3,
        Severity.MAJOR, 0.6,
        Severity.CRITICAL, 1.0
    );
    
    // Default dimension weights
    private static final Map<Dimension, Double> DEFAULT_DIMENSION_WEIGHTS = Map.of(
        Dimension.SECURITY, 0.25,
        Dimension.QUALITY, 0.30,
        Dimension.MAINTAINABILITY, 0.25,
        Dimension.PERFORMANCE, 0.15,
        Dimension.TEST_COVERAGE, 0.05
    );
    
    /**
     * Calculate scores for a list of findings using default configuration.
     * 
     * @param findings list of findings to score
     * @return calculated scores with dimension breakdown
     */
    public Scores calculateScores(List<Finding> findings) {
        return calculateScores(findings, ScoringConfig.defaultConfig());
    }
    
    /**
     * Calculate scores for a list of findings with lines changed context.
     * 
     * @param findings list of findings to score
     * @param linesChanged number of lines changed (ignored for now)
     * @return calculated scores with dimension breakdown
     */
    public Scores calculateScores(List<Finding> findings, int linesChanged) {
        return calculateScores(findings, ScoringConfig.defaultConfig());
    }
    
    /**
     * Calculate scores for a list of findings using external config.
     * 
     * @param findings list of findings to score
     * @param linesChanged number of lines changed (ignored for now)
     * @param externalConfig external scoring configuration
     * @return calculated scores with dimension breakdown
     */
    public Scores calculateScores(List<Finding> findings, int linesChanged, 
                                com.ai.reviewer.backend.domain.config.AiReviewConfig.ScoringConfig externalConfig) {
        // Convert external config to internal config
        ScoringConfig config = new ScoringConfig(
            100.0,  // baseScore
            0.0,    // minScore
            DEFAULT_SEVERITY_IMPACT,
            externalConfig.weights(),
            1.2,    // confidenceExponent
            70.0,   // problemThreshold
            90.0    // excellentThreshold
        );
        return calculateScores(findings, config);
    }
    
    /**
     * Calculate scores for a list of findings using external config.
     * 
     * @param findings list of findings to score
     * @param externalConfig external scoring configuration
     * @return calculated scores with dimension breakdown
     */
    public Scores calculateScores(List<Finding> findings, 
                                com.ai.reviewer.backend.domain.config.AiReviewConfig.ScoringConfig externalConfig) {
        // Convert external config to internal config
        ScoringConfig config = new ScoringConfig(
            100.0,  // baseScore
            0.0,    // minScore
            DEFAULT_SEVERITY_IMPACT,
            externalConfig.weights(),
            1.2,    // confidenceExponent
            70.0,   // problemThreshold
            90.0    // excellentThreshold
        );
        return calculateScores(findings, config);
    }

    /**
     * Calculate scores for a list of findings.
     * 
     * @param findings list of findings to score
     * @param config scoring configuration
     * @return calculated scores with dimension breakdown
     */
    public Scores calculateScores(List<Finding> findings, ScoringConfig config) {
        if (findings.isEmpty()) {
            return createPerfectScores(config.dimensionWeights());
        }
        
        // Group findings by dimension
        Map<Dimension, List<Finding>> findingsByDimension = groupFindingsByDimension(findings);
        
        // Calculate dimension-specific scores
        Map<Dimension, Double> dimensionScores = new HashMap<>();
        for (Dimension dimension : Dimension.values()) {
            List<Finding> dimensionFindings = findingsByDimension.getOrDefault(dimension, List.of());
            double score = calculateDimensionScore(dimensionFindings, dimension, config);
            dimensionScores.put(dimension, score);
        }
        
        // Calculate total weighted score
        double totalScore = calculateTotalScore(dimensionScores, config.dimensionWeights());
        
        return new Scores(totalScore, dimensionScores, config.dimensionWeights());
    }
    
    /**
     * Group findings by their primary dimension.
     */
    private Map<Dimension, List<Finding>> groupFindingsByDimension(List<Finding> findings) {
        return findings.stream()
            .collect(Collectors.groupingBy(Finding::dimension));
    }
    
    /**
     * Calculate score for a specific dimension.
     */
    private double calculateDimensionScore(List<Finding> findings, Dimension dimension, ScoringConfig config) {
        if (findings.isEmpty()) {
            return config.baseScore(); // Perfect score when no issues
        }
        
        double totalImpact = 0.0;
        double maxPossibleImpact = 0.0;
        
        for (Finding finding : findings) {
            double severityImpact = config.severityImpact().getOrDefault(finding.severity(), 0.5);
            double confidenceWeight = Math.pow(finding.confidence(), config.confidenceExponent());
            double findingImpact = severityImpact * confidenceWeight;
            
            totalImpact += findingImpact;
            maxPossibleImpact += 1.0; // Maximum impact per finding
        }
        
        // Apply dimension-specific scoring rules
        double adjustedImpact = applyDimensionRules(totalImpact, findings, dimension, config);
        
        // Calculate final score (inverted - less impact means higher score)
        double impactRatio = maxPossibleImpact > 0 ? adjustedImpact / maxPossibleImpact : 0.0;
        double score = Math.max(0.0, config.baseScore() - (impactRatio * config.baseScore()));
        
        // Apply minimum score threshold
        return Math.max(config.minScore(), score);
    }
    
    /**
     * Apply dimension-specific scoring rules and adjustments.
     */
    private double applyDimensionRules(double baseImpact, List<Finding> findings, 
                                     Dimension dimension, ScoringConfig config) {
        return switch (dimension) {
            case SECURITY -> applySecurityRules(baseImpact, findings, config);
            case QUALITY -> applyQualityRules(baseImpact, findings, config);
            case MAINTAINABILITY -> applyMaintainabilityRules(baseImpact, findings, config);
            case PERFORMANCE -> applyPerformanceRules(baseImpact, findings, config);
            case TEST_COVERAGE -> applyTestCoverageRules(baseImpact, findings, config);
        };
    }
    
    /**
     * Apply security-specific scoring rules.
     */
    private double applySecurityRules(double baseImpact, List<Finding> findings, ScoringConfig config) {
        double adjustedImpact = baseImpact;
        
        // Security issues are often critical - amplify critical findings
        long criticalCount = findings.stream()
            .filter(f -> f.severity() == Severity.CRITICAL)
            .count();
        
        if (criticalCount > 0) {
            adjustedImpact *= (1.0 + criticalCount * 0.5); // 50% penalty per critical issue
        }
        
        // High confidence security issues get extra weight
        double avgConfidence = findings.stream()
            .mapToDouble(Finding::confidence)
            .average()
            .orElse(0.5);
        
        if (avgConfidence > 0.8) {
            adjustedImpact *= 1.3; // 30% penalty for high-confidence security issues
        }
        
        return adjustedImpact;
    }
    
    /**
     * Apply quality-specific scoring rules.
     */
    private double applyQualityRules(double baseImpact, List<Finding> findings, ScoringConfig config) {
        double adjustedImpact = baseImpact;
        
        // Many minor issues can indicate systemic quality problems
        long minorCount = findings.stream()
            .filter(f -> f.severity() == Severity.MINOR)
            .count();
        
        if (minorCount > 10) {
            adjustedImpact *= 1.2; // 20% penalty for many minor issues
        }
        
        return adjustedImpact;
    }
    
    /**
     * Apply maintainability-specific scoring rules.
     */
    private double applyMaintainabilityRules(double baseImpact, List<Finding> findings, ScoringConfig config) {
        double adjustedImpact = baseImpact;
        
        // Complex issues affect maintainability more
        double avgSeverityScore = findings.stream()
            .mapToDouble(f -> f.severity().ordinal())
            .average()
            .orElse(0.0);
        
        if (avgSeverityScore > 2.0) { // Average above MINOR
            adjustedImpact *= 1.15;
        }
        
        return adjustedImpact;
    }
    
    /**
     * Apply performance-specific scoring rules.
     */
    private double applyPerformanceRules(double baseImpact, List<Finding> findings, ScoringConfig config) {
        // Performance issues are often context-dependent
        // Reduce impact if confidence is low
        double avgConfidence = findings.stream()
            .mapToDouble(Finding::confidence)
            .average()
            .orElse(0.5);
        
        if (avgConfidence < 0.6) {
            return baseImpact * 0.8; // Reduce impact by 20% for low confidence
        }
        
        return baseImpact;
    }
    
    /**
     * Apply test coverage-specific scoring rules.
     */
    private double applyTestCoverageRules(double baseImpact, List<Finding> findings, ScoringConfig config) {
        // Test coverage issues are usually systematic
        // Single finding might indicate broader issues
        if (findings.size() == 1) {
            return baseImpact * 1.5; // 50% penalty increase
        }
        
        return baseImpact;
    }
    
    /**
     * Calculate total weighted score across dimensions.
     */
    private double calculateTotalScore(Map<Dimension, Double> dimensionScores, 
                                     Map<Dimension, Double> weights) {
        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<Dimension, Double> entry : dimensionScores.entrySet()) {
            Dimension dimension = entry.getKey();
            Double score = entry.getValue();
            Double weight = weights.getOrDefault(dimension, 0.0);
            
            totalWeightedScore += score * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? totalWeightedScore / totalWeight * 100 : 100.0;
    }
    
    /**
     * Create perfect scores when no findings exist.
     */
    private Scores createPerfectScores(Map<Dimension, Double> weights) {
        Map<Dimension, Double> dimensionScores = new HashMap<>();
        for (Dimension dimension : Dimension.values()) {
            dimensionScores.put(dimension, 100.0);
        }
        return new Scores(100.0, dimensionScores, weights);
    }
    
    /**
     * Map findings to appropriate dimensions based on content analysis.
     */
    public List<Finding> mapFindingsToDimensions(List<Finding> findings, DimensionMappingConfig config) {
        return findings.stream()
            .map(finding -> mapFindingToDimension(finding, config))
            .collect(Collectors.toList());
    }
    
    /**
     * Map a single finding to the most appropriate dimension.
     */
    private Finding mapFindingToDimension(Finding finding, DimensionMappingConfig config) {
        // If finding already has a dimension and we're not forcing remapping, keep it
        if (!config.forceRemapping() && finding.dimension() != null) {
            return finding;
        }
        
        Dimension mappedDimension = determineBestDimension(finding, config);
        
        // Return new finding with mapped dimension
        return new Finding(
            finding.id(),
            finding.file(),
            finding.startLine(),
            finding.endLine(),
            finding.severity(),
            mappedDimension,
            finding.title(),
            finding.evidence(),
            finding.suggestion(),
            finding.patch(),
            finding.sources(),
            finding.confidence()
        );
    }
    
    /**
     * Determine the best dimension for a finding based on keywords and patterns.
     */
    private Dimension determineBestDimension(Finding finding, DimensionMappingConfig config) {
        String content = (finding.message() + " " + finding.file()).toLowerCase();
        
        Map<Dimension, Integer> dimensionScores = new HashMap<>();
        
        // Score each dimension based on keyword matches
        for (Dimension dimension : Dimension.values()) {
            int score = calculateDimensionMatchScore(content, dimension, config);
            dimensionScores.put(dimension, score);
        }
        
        // Return dimension with highest score, or default if no matches
        return dimensionScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .filter(entry -> entry.getValue() > 0)
            .map(Map.Entry::getKey)
            .orElse(config.defaultDimension());
    }
    
    /**
     * Calculate match score for a dimension based on keywords.
     */
    private int calculateDimensionMatchScore(String content, Dimension dimension, DimensionMappingConfig config) {
        List<String> keywords = config.dimensionKeywords().getOrDefault(dimension, List.of());
        
        int score = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase())) {
                score += config.keywordWeight();
            }
        }
        
        return score;
    }
    
    /**
     * Generate score summary with insights.
     */
    public ScoreSummary generateScoreSummary(Scores scores, List<Finding> findings, ScoringConfig config) {
        // Identify problem areas
        List<Dimension> problemAreas = scores.dimensions().entrySet().stream()
            .filter(entry -> entry.getValue() < config.problemThreshold())
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Identify strong areas
        List<Dimension> strongAreas = scores.dimensions().entrySet().stream()
            .filter(entry -> entry.getValue() >= config.excellentThreshold())
            .sorted(Map.Entry.<Dimension, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Calculate improvement potential
        double improvementPotential = calculateImprovementPotential(scores, findings, config);
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(problemAreas, findings, config);
        
        return new ScoreSummary(
            scores,
            getScoreGrade(scores.totalScore()),
            problemAreas,
            strongAreas,
            improvementPotential,
            recommendations,
            calculateTrendIndicators(scores, config)
        );
    }
    
    /**
     * Calculate improvement potential based on current scores and findings.
     */
    private double calculateImprovementPotential(Scores scores, List<Finding> findings, ScoringConfig config) {
        // Count addressable issues (high confidence, actionable findings)
        long addressableIssues = findings.stream()
            .filter(f -> f.confidence() > 0.7)
            .filter(f -> f.severity().ordinal() >= Severity.MINOR.ordinal())
            .count();
        
        double currentScore = scores.totalScore();
        double maxImprovementFromIssues = Math.min(20.0, addressableIssues * 2.0); // Max 2 points per issue, cap at 20
        
        return Math.min(100.0 - currentScore, maxImprovementFromIssues);
    }
    
    /**
     * Generate recommendations based on problem areas.
     */
    private List<String> generateRecommendations(List<Dimension> problemAreas, List<Finding> findings, ScoringConfig config) {
        List<String> recommendations = new ArrayList<>();
        
        for (Dimension dimension : problemAreas) {
            switch (dimension) {
                case SECURITY -> recommendations.add("Review security-related findings and implement security best practices");
                case QUALITY -> recommendations.add("Address code quality issues and consider refactoring complex areas");
                case MAINTAINABILITY -> recommendations.add("Improve code maintainability through better documentation and structure");
                case PERFORMANCE -> recommendations.add("Investigate performance issues and optimize critical paths");
                case TEST_COVERAGE -> recommendations.add("Increase test coverage and add missing test cases");
            }
        }
        
        return recommendations.isEmpty() ? 
            List.of("Great work! Continue following best practices.") : 
            recommendations;
    }
    
    /**
     * Get letter grade for total score.
     */
    private ScoreGrade getScoreGrade(double score) {
        if (score >= 90) return ScoreGrade.A;
        if (score >= 80) return ScoreGrade.B;
        if (score >= 70) return ScoreGrade.C;
        if (score >= 60) return ScoreGrade.D;
        return ScoreGrade.F;
    }
    
    /**
     * Calculate trend indicators (for future use with historical data).
     */
    private Map<String, Object> calculateTrendIndicators(Scores scores, ScoringConfig config) {
        Map<String, Object> trends = new HashMap<>();
        trends.put("totalScore", scores.totalScore());
        trends.put("timestamp", java.time.Instant.now());
        // TODO: Add historical comparison when data is available
        return trends;
    }
    
    /**
     * Scoring configuration.
     */
    public record ScoringConfig(
        double baseScore,
        double minScore,
        Map<Severity, Double> severityImpact,
        Map<Dimension, Double> dimensionWeights,
        double confidenceExponent,
        double problemThreshold,
        double excellentThreshold
    ) {
        public static ScoringConfig defaultConfig() {
            return new ScoringConfig(
                100.0,  // baseScore
                0.0,    // minScore
                DEFAULT_SEVERITY_IMPACT,
                DEFAULT_DIMENSION_WEIGHTS,
                1.2,    // confidenceExponent
                70.0,   // problemThreshold
                90.0    // excellentThreshold
            );
        }
        
        public static ScoringConfig strict() {
            return new ScoringConfig(
                100.0,
                0.0,
                Map.of(
                    Severity.INFO, 0.2,
                    Severity.MINOR, 0.5,
                    Severity.MAJOR, 0.8,
                    Severity.CRITICAL, 1.2
                ),
                DEFAULT_DIMENSION_WEIGHTS,
                1.5,    // Higher confidence exponent
                80.0,   // Higher problem threshold
                95.0    // Higher excellence threshold
            );
        }
        
        public static ScoringConfig lenient() {
            return new ScoringConfig(
                100.0,
                20.0,   // Higher minimum score
                Map.of(
                    Severity.INFO, 0.05,
                    Severity.MINOR, 0.2,
                    Severity.MAJOR, 0.4,
                    Severity.CRITICAL, 0.7
                ),
                DEFAULT_DIMENSION_WEIGHTS,
                1.0,    // Linear confidence weighting
                60.0,   // Lower problem threshold
                80.0    // Lower excellence threshold
            );
        }
    }
    
    /**
     * Dimension mapping configuration.
     */
    public record DimensionMappingConfig(
        Map<Dimension, List<String>> dimensionKeywords,
        Dimension defaultDimension,
        int keywordWeight,
        boolean forceRemapping
    ) {
        public static DimensionMappingConfig defaultConfig() {
            Map<Dimension, List<String>> keywords = Map.of(
                Dimension.SECURITY, List.of("security", "vulnerability", "injection", "xss", "csrf", "auth", "crypto", "ssl", "password", "token"),
                Dimension.QUALITY, List.of("quality", "bug", "error", "exception", "null", "duplicate", "complexity", "smell", "antipattern"),
                Dimension.MAINTAINABILITY, List.of("maintainability", "refactor", "documentation", "comment", "naming", "structure", "coupling", "cohesion"),
                Dimension.PERFORMANCE, List.of("performance", "slow", "memory", "cpu", "optimization", "efficiency", "cache", "database", "query", "loop"),
                Dimension.TEST_COVERAGE, List.of("test", "coverage", "unittest", "integration", "assertion", "mock", "stub")
            );
            
            return new DimensionMappingConfig(
                keywords,
                Dimension.QUALITY,  // default dimension
                1,                  // keyword weight
                false              // don't force remapping
            );
        }
    }
    
    /**
     * Score grades.
     */
    public enum ScoreGrade {
        A, B, C, D, F
    }
    
    /**
     * Comprehensive score summary.
     */
    public record ScoreSummary(
        Scores scores,
        ScoreGrade grade,
        List<Dimension> problemAreas,
        List<Dimension> strongAreas,
        double improvementPotential,
        List<String> recommendations,
        Map<String, Object> trendIndicators
    ) {
        public boolean hasProblems() {
            return !problemAreas.isEmpty();
        }
        
        public boolean isExcellent() {
            return grade == ScoreGrade.A && problemAreas.isEmpty();
        }
        
        public String getSummaryText() {
            return String.format("Overall Grade: %s (%.1f/100) - %s", 
                grade, scores.totalScore(),
                hasProblems() ? 
                    problemAreas.size() + " areas need attention" : 
                    "All quality dimensions look good");
        }
    }
}
