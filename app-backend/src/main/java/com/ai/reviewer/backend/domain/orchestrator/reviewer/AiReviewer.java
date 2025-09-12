package com.ai.reviewer.backend.domain.orchestrator.reviewer;

import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import com.ai.reviewer.shared.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI-powered code review interface using Large Language Models.
 * 
 * <p>Implementations should leverage AI/LLM capabilities to provide
 * intelligent code review insights, including best practices,
 * architectural recommendations, and contextual analysis.
 */
public interface AiReviewer {
    
    /**
     * Get the unique identifier for this AI reviewer.
     * 
     * @return reviewer identifier (e.g., "openai-gpt4", "claude-3", "local-llm")
     */
    String getReviewerId();
    
    /**
     * Get human-readable name for this reviewer.
     * 
     * @return reviewer display name
     */
    String getReviewerName();
    
    /**
     * Get the model version being used.
     * 
     * @return model version string
     */
    String getModelVersion();
    
    /**
     * Check if this reviewer supports the given programming language.
     * 
     * @param language programming language (java, python, etc.)
     * @return true if reviewer can analyze this language effectively
     */
    boolean supportsLanguage(String language);
    
    /**
     * Check if this reviewer is enabled and has valid configuration.
     * 
     * @return true if reviewer is ready to use
     */
    boolean isEnabled();
    
    /**
     * Review code changes using diff hunks.
     * 
     * <p>This is a simplified interface for reviewing code changes based on
     * diff hunks from pull requests. This method should analyze the provided
     * diff hunks using AI capabilities and provide intelligent insights.
     * 
     * @param repository repository reference
     * @param pullRequest pull request reference  
     * @param diffHunks list of diff hunks to review
     * @param config AI review configuration
     * @return list of AI-generated findings from the review
     */
    List<Finding> review(RepoRef repository, PullRef pullRequest, List<DiffHunk> diffHunks, AiReviewConfig config);
    
    /**
     * Review a code segment asynchronously using AI.
     * 
     * <p>This method should analyze the code segment using AI capabilities
     * and provide intelligent insights about code quality, best practices,
     * potential bugs, and improvements.
     * 
     * @param segment code segment to review
     * @param context review context with repository and PR information
     * @return CompletableFuture containing list of AI-generated findings
     */
    CompletableFuture<List<Finding>> reviewAsync(StaticAnalyzer.CodeSegment segment, ReviewContext context);
    
    /**
     * Review multiple code segments with cross-segment analysis.
     * 
     * <p>This method allows the AI to analyze multiple segments together,
     * potentially identifying issues that span across segments or providing
     * more contextual insights.
     * 
     * @param segments list of related code segments
     * @param context review context
     * @return CompletableFuture containing findings from comprehensive review
     */
    CompletableFuture<List<Finding>> reviewBatchAsync(List<StaticAnalyzer.CodeSegment> segments, ReviewContext context);
    
    /**
     * Generate a summary review for the entire change set.
     * 
     * <p>This method provides a high-level analysis of all changes,
     * including overall code quality assessment, architectural insights,
     * and recommendations for the entire PR/change set.
     * 
     * @param allSegments all code segments in the change set
     * @param staticFindings findings from static analysis tools (for context)
     * @param context review context
     * @return CompletableFuture containing a summary finding
     */
    CompletableFuture<Finding> generateSummaryAsync(List<StaticAnalyzer.CodeSegment> allSegments, 
                                                   List<Finding> staticFindings, 
                                                   ReviewContext context);
    
    /**
     * Get supported review dimensions for this AI reviewer.
     * 
     * @return list of dimensions this reviewer can evaluate
     */
    default List<com.ai.reviewer.shared.enums.Dimension> getSupportedDimensions() {
        return List.of(
            com.ai.reviewer.shared.enums.Dimension.QUALITY,
            com.ai.reviewer.shared.enums.Dimension.MAINTAINABILITY,
            com.ai.reviewer.shared.enums.Dimension.SECURITY,
            com.ai.reviewer.shared.enums.Dimension.PERFORMANCE
        );
    }
    
    /**
     * Get configuration requirements for this AI reviewer.
     * 
     * @return list of required configuration keys
     */
    default List<String> getConfigurationKeys() {
        return List.of("apiKey", "model", "maxTokens", "temperature");
    }
    
    /**
     * Validate reviewer configuration.
     * 
     * @param config configuration map
     * @return validation result with any errors
     */
    default ReviewerValidationResult validateConfiguration(ReviewerConfig config) {
        return ReviewerValidationResult.success();
    }
    
    /**
     * Get reviewer usage statistics.
     * 
     * @return reviewer statistics
     */
    default ReviewerStats getStats() {
        return ReviewerStats.empty();
    }
    
    /**
     * Estimate token usage for a review request.
     * 
     * @param segment code segment to estimate
     * @param context review context
     * @return estimated token count
     */
    default int estimateTokenUsage(StaticAnalyzer.CodeSegment segment, ReviewContext context) {
        // Simple estimation: ~4 characters per token
        int codeTokens = segment.content().length() / 4;
        int promptTokens = 500; // Estimated prompt overhead
        return codeTokens + promptTokens;
    }
    
    /**
     * Review context containing additional information for AI analysis.
     */
    record ReviewContext(
        RepoRef repository,
        PullRef pullRequest,
        String runId,
        ReviewerConfig config,
        ReviewPromptTemplate promptTemplate,
        List<String> additionalContext,
        java.time.Instant startTime
    ) {
        /**
         * Create a basic review context.
         */
        public static ReviewContext basic(RepoRef repo, PullRef pull, String runId, ReviewerConfig config) {
            return new ReviewContext(repo, pull, runId, config, 
                ReviewPromptTemplate.defaultTemplate(), List.of(), java.time.Instant.now());
        }
        
        /**
         * Add additional context information.
         */
        public ReviewContext withContext(String... context) {
            List<String> newContext = new java.util.ArrayList<>(additionalContext);
            newContext.addAll(List.of(context));
            return new ReviewContext(repository, pullRequest, runId, config, 
                promptTemplate, newContext, startTime);
        }
    }
    
    /**
     * AI reviewer configuration.
     */
    record ReviewerConfig(
        String reviewerId,
        java.util.Map<String, Object> properties,
        boolean enabled,
        int timeoutSeconds,
        int maxConcurrentRequests,
        ReviewerLimits limits
    ) {
        public static ReviewerConfig empty(String reviewerId) {
            return new ReviewerConfig(reviewerId, java.util.Map.of(), true, 60, 5,
                new ReviewerLimits(8000, 2000, 1000, 0.7));
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, T defaultValue) {
            return (T) properties.getOrDefault(key, defaultValue);
        }
        
        public String getApiKey() {
            return getProperty("apiKey", "");
        }
        
        public String getModel() {
            return getProperty("model", "gpt-4");
        }
        
        public int getMaxTokens() {
            return getProperty("maxTokens", 2000);
        }
        
        public double getTemperature() {
            return getProperty("temperature", 0.3);
        }
    }
    
    /**
     * Reviewer resource limits.
     */
    record ReviewerLimits(
        int maxInputTokens,
        int maxOutputTokens,
        int maxSegmentSize,
        double maxTemperature
    ) {}
    
    /**
     * Review prompt template for AI requests.
     */
    record ReviewPromptTemplate(
        String systemPrompt,
        String userPrompt,
        String summaryPrompt,
        java.util.Map<String, String> placeholders
    ) {
        public static ReviewPromptTemplate defaultTemplate() {
            String systemPrompt = """
                You are an expert code reviewer with deep knowledge of software engineering best practices.
                Analyze the provided code for:
                - Code quality and maintainability
                - Security vulnerabilities
                - Performance issues
                - Best practices violations
                - Potential bugs or logic errors
                
                Provide constructive, actionable feedback with specific suggestions for improvement.
                Focus on the most important issues and avoid nitpicking on minor style issues.
                """;
            
            String userPrompt = """
                Please review the following code segment:
                
                File: {{filePath}}
                Language: {{language}}
                Lines {{startLine}}-{{endLine}}:
                
                ```{{language}}
                {{content}}
                ```
                
                {{#additionalContext}}
                Additional Context:
                {{#each additionalContext}}
                - {{this}}
                {{/each}}
                {{/additionalContext}}
                
                Please provide your review findings in a structured format.
                """;
            
            String summaryPrompt = """
                Based on your analysis of the code changes, provide a comprehensive summary including:
                - Overall code quality assessment
                - Most critical issues found
                - Architectural observations
                - Recommendations for improvement
                """;
            
            return new ReviewPromptTemplate(systemPrompt, userPrompt, summaryPrompt, java.util.Map.of());
        }
        
        /**
         * Replace placeholders in template.
         */
        public String fillTemplate(String template, java.util.Map<String, Object> values) {
            String result = template;
            for (var entry : values.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
            return result;
        }
    }
    
    /**
     * Reviewer validation result.
     */
    record ReviewerValidationResult(
        boolean valid,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
        public static ReviewerValidationResult success() {
            return new ReviewerValidationResult(true, List.of(), List.of());
        }
        
        public static ReviewerValidationResult invalid(String... errors) {
            return new ReviewerValidationResult(false, List.of(errors), List.of());
        }
        
        public static ReviewerValidationResult withWarnings(String... warnings) {
            return new ReviewerValidationResult(true, List.of(), List.of(warnings));
        }
    }
    
    /**
     * Reviewer execution statistics.
     */
    record ReviewerStats(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long totalTokensUsed,
        double avgResponseTimeMs,
        double avgTokensPerRequest,
        java.time.Instant lastRequestTime,
        String lastError
    ) {
        public static ReviewerStats empty() {
            return new ReviewerStats(0, 0, 0, 0, 0.0, 0.0, null, null);
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0.0;
        }
        
        public double getCostEstimate(double tokenCostPer1K) {
            return (totalTokensUsed / 1000.0) * tokenCostPer1K;
        }
    }
}
