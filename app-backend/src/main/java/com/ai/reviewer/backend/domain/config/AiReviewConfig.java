package com.ai.reviewer.backend.domain.config;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/**
 * AI 评审配置。
 * 
 * <p>对应仓库根目录的 .ai-review.yml 文件配置结构。
 */
public record AiReviewConfig(
    
    /**
     * SCM 提供商。
     */
    @NotNull
    @Pattern(regexp = "github|gitlab", message = "provider must be 'github' or 'gitlab'")
    String provider,
    
    /**
     * LLM 配置。
     */
    @NotNull
    @Valid
    LlmConfig llm,
    
    /**
     * 评分配置。
     */
    @NotNull
    @Valid
    ScoringConfig scoring,
    
    /**
     * 报告配置。
     */
    @NotNull
    @Valid
    ReportConfig report
) {
    
    /**
     * LLM 配置。
     */
    public record LlmConfig(
        /**
         * LLM 适配器列表。
         */
        @NotEmpty(message = "At least one LLM adapter must be specified")
        List<@NotBlank String> adapters,
        
        /**
         * 预算限制（美元）。
         */
        @JsonProperty("budget_usd")
        @DecimalMin(value = "0.0", inclusive = false, message = "Budget must be positive")
        @DecimalMax(value = "100.0", message = "Budget must not exceed $100")
        double budgetUsd
    ) {}
    
    /**
     * 评分配置。
     */
    public record ScoringConfig(
        /**
         * 维度权重配置。
         */
        @NotNull
        @Valid
        Map<Dimension, @DecimalMin("0.0") @DecimalMax("1.0") Double> weights,
        
        /**
         * 严重性惩罚分数。
         */
        @NotNull
        @Valid
        Map<Severity, @DecimalMin("0.0") Double> severityPenalty,
        
        /**
         * 忽略置信度阈值。
         */
        @DecimalMin(value = "0.0", message = "Confidence threshold must be non-negative")
        @DecimalMax(value = "1.0", message = "Confidence threshold must not exceed 1.0")
        double ignoreConfidenceBelow
    ) {
        /**
         * 验证权重总和是否为1.0。
         */
        public void validateWeights() {
            if (weights == null || weights.isEmpty()) {
                throw new IllegalArgumentException("Weights cannot be null or empty");
            }
            
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new IllegalArgumentException(
                    String.format("Weights must sum to 1.0, but got %.3f", sum)
                );
            }
            
            // 检查是否包含所有必需的维度
            for (Dimension dimension : Dimension.values()) {
                if (!weights.containsKey(dimension)) {
                    throw new IllegalArgumentException(
                        "Missing weight for dimension: " + dimension
                    );
                }
            }
        }
        
        /**
         * 验证严重性惩罚配置。
         */
        public void validateSeverityPenalty() {
            if (severityPenalty == null || severityPenalty.isEmpty()) {
                throw new IllegalArgumentException("Severity penalty cannot be null or empty");
            }
            
            // 检查是否包含所有严重性级别
            for (Severity severity : Severity.values()) {
                if (!severityPenalty.containsKey(severity)) {
                    throw new IllegalArgumentException(
                        "Missing penalty for severity: " + severity
                    );
                }
            }
            
            // 验证惩罚分数递增
            double infoPenalty = severityPenalty.get(Severity.INFO);
            double minorPenalty = severityPenalty.get(Severity.MINOR);
            double majorPenalty = severityPenalty.get(Severity.MAJOR);
            double criticalPenalty = severityPenalty.get(Severity.CRITICAL);
            
            if (!(infoPenalty <= minorPenalty && minorPenalty <= majorPenalty && majorPenalty <= criticalPenalty)) {
                throw new IllegalArgumentException(
                    "Severity penalties must be in ascending order: INFO <= MINOR <= MAJOR <= CRITICAL"
                );
            }
        }
    }
    
    /**
     * 报告配置。
     */
    public record ReportConfig(
        /**
         * 导出配置。
         */
        @NotNull
        @Valid
        ExportConfig export
    ) {
        
        /**
         * 导出格式配置。
         */
        public record ExportConfig(
            /**
             * 是否导出 SARIF 格式。
             */
            boolean sarif,
            
            /**
             * 是否导出 JSON 格式。
             */
            boolean json,
            
            /**
             * 是否导出 PDF 格式。
             */
            boolean pdf,
            
            /**
             * 是否导出 HTML 格式。
             */
            boolean html
        ) {
            /**
             * 检查是否至少启用一种导出格式。
             */
            public void validateAtLeastOneFormat() {
                if (!(sarif || json || pdf || html)) {
                    throw new IllegalArgumentException(
                        "At least one export format must be enabled"
                    );
                }
            }
        }
    }
    
    /**
     * 获取默认配置。
     */
    public static AiReviewConfig getDefault() {
        return new AiReviewConfig(
            "github",
            new LlmConfig(
                List.of("gpt-4o", "claude-3.5-sonnet"),
                0.50
            ),
            new ScoringConfig(
                Map.of(
                    Dimension.SECURITY, 0.30,
                    Dimension.QUALITY, 0.25,
                    Dimension.MAINTAINABILITY, 0.20,
                    Dimension.PERFORMANCE, 0.15,
                    Dimension.TEST_COVERAGE, 0.10
                ),
                Map.of(
                    Severity.INFO, 1.0,
                    Severity.MINOR, 3.0,
                    Severity.MAJOR, 7.0,
                    Severity.CRITICAL, 12.0
                ),
                0.3
            ),
            new ReportConfig(
                new ReportConfig.ExportConfig(true, true, true, true)
            )
        );
    }
    
    /**
     * 验证整个配置的有效性。
     */
    public void validate() {
        if (scoring != null) {
            scoring.validateWeights();
            scoring.validateSeverityPenalty();
        }
        
        if (report != null && report.export() != null) {
            report.export().validateAtLeastOneFormat();
        }
    }
}
