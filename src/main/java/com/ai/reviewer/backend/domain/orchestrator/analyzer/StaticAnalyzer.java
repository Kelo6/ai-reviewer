package com.ai.reviewer.backend.domain.orchestrator.analyzer;

import com.ai.reviewer.shared.model.*;
import com.ai.reviewer.backend.domain.config.AiReviewConfig;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Static code analysis interface for detecting issues without LLM.
 * 
 * <p>Implementations should focus on specific aspects of code quality
 * such as security vulnerabilities, code smells, performance issues,
 * or style violations using rule-based analysis.
 */
public interface StaticAnalyzer {
    
    /**
     * Get the unique identifier for this analyzer.
     * 
     * @return analyzer identifier (e.g., "spotbugs", "checkstyle", "pmd")
     */
    String getAnalyzerId();
    
    /**
     * Get human-readable name for this analyzer.
     * 
     * @return analyzer display name
     */
    String getAnalyzerName();
    
    /**
     * Get the version of this analyzer.
     * 
     * @return version string
     */
    String getVersion();
    
    /**
     * Check if this analyzer supports the given file type.
     * 
     * @param fileName file name to check
     * @return true if analyzer can process this file type
     */
    boolean supportsFile(String fileName);
    
    /**
     * Check if this analyzer is enabled and properly configured.
     * 
     * @return true if analyzer is ready to use
     */
    boolean isEnabled();
    
    /**
     * Analyze code changes using diff hunks.
     * 
     * <p>This is a simplified interface for analyzing code changes based on
     * diff hunks from pull requests. This method should analyze the provided
     * diff hunks and return findings related to the analyzer's focus area.
     * 
     * @param repository repository reference
     * @param pullRequest pull request reference  
     * @param diffHunks list of diff hunks to analyze
     * @param config AI review configuration
     * @return list of findings discovered during analysis
     */
    List<Finding> analyze(RepoRef repository, PullRef pullRequest, List<DiffHunk> diffHunks, AiReviewConfig config);
    
    /**
     * Analyze a code segment asynchronously.
     * 
     * <p>This method should analyze the provided code segment and return
     * findings related to the analyzer's focus area. The analysis should
     * be performed asynchronously to allow for concurrent processing.
     * 
     * @param segment code segment to analyze
     * @param context analysis context with repository and PR information
     * @return CompletableFuture containing list of findings
     */
    CompletableFuture<List<Finding>> analyzeAsync(CodeSegment segment, AnalysisContext context);
    
    /**
     * Analyze multiple code segments in batch.
     * 
     * <p>Default implementation processes each segment individually,
     * but analyzers may override this for more efficient batch processing.
     * 
     * @param segments list of code segments to analyze
     * @param context analysis context
     * @return CompletableFuture containing all findings from all segments
     */
    default CompletableFuture<List<Finding>> analyzeBatchAsync(List<CodeSegment> segments, AnalysisContext context) {
        List<CompletableFuture<List<Finding>>> futures = segments.stream()
            .map(segment -> analyzeAsync(segment, context))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .flatMap(future -> future.join().stream())
                .toList());
    }
    
    /**
     * Get configuration requirements for this analyzer.
     * 
     * @return list of required configuration keys
     */
    default List<String> getConfigurationKeys() {
        return List.of();
    }
    
    /**
     * Validate analyzer configuration.
     * 
     * @param config configuration map
     * @return validation result with any errors
     */
    default AnalyzerValidationResult validateConfiguration(AnalyzerConfig config) {
        return AnalyzerValidationResult.success();
    }
    
    /**
     * Get analyzer statistics (calls, errors, avg processing time, etc.).
     * 
     * @return analyzer statistics
     */
    default AnalyzerStats getStats() {
        return AnalyzerStats.empty();
    }
    
    /**
     * Code segment to be analyzed.
     */
    record CodeSegment(
        String filePath,
        String content,
        int startLine,
        int endLine,
        String language,
        SegmentType type,
        SegmentMetadata metadata
    ) {
        /**
         * Create a simple code segment.
         */
        public static CodeSegment of(String filePath, String content, int startLine, int endLine) {
            return new CodeSegment(filePath, content, startLine, endLine, 
                detectLanguage(filePath), SegmentType.LINES, SegmentMetadata.empty());
        }
        
        /**
         * Create a function-based code segment.
         */
        public static CodeSegment function(String filePath, String content, int startLine, int endLine, 
                                         String functionName, String language) {
            return new CodeSegment(filePath, content, startLine, endLine, language, 
                SegmentType.FUNCTION, SegmentMetadata.of("functionName", functionName));
        }
        
        /**
         * Create a class-based code segment.
         */
        public static CodeSegment clazz(String filePath, String content, int startLine, int endLine,
                                      String className, String language) {
            return new CodeSegment(filePath, content, startLine, endLine, language,
                SegmentType.CLASS, SegmentMetadata.of("className", className));
        }
        
        /**
         * Detect programming language from file extension.
         */
        private static String detectLanguage(String filePath) {
            String ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
            return switch (ext) {
                case "java" -> "java";
                case "js", "jsx" -> "javascript";
                case "ts", "tsx" -> "typescript";
                case "py" -> "python";
                case "cpp", "cc", "cxx" -> "cpp";
                case "c" -> "c";
                case "cs" -> "csharp";
                case "go" -> "go";
                case "rs" -> "rust";
                case "kt" -> "kotlin";
                case "scala" -> "scala";
                case "rb" -> "ruby";
                case "php" -> "php";
                default -> "text";
            };
        }
    }
    
    /**
     * Analysis context containing repository and PR information.
     */
    record AnalysisContext(
        RepoRef repository,
        PullRef pullRequest,
        String runId,
        AnalyzerConfig config,
        java.time.Instant startTime
    ) {}
    
    /**
     * Analyzer configuration.
     */
    record AnalyzerConfig(
        String analyzerId,
        java.util.Map<String, Object> properties,
        boolean enabled,
        int timeoutSeconds
    ) {
        public static AnalyzerConfig empty(String analyzerId) {
            return new AnalyzerConfig(analyzerId, java.util.Map.of(), true, 30);
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, T defaultValue) {
            return (T) properties.getOrDefault(key, defaultValue);
        }
    }
    
    /**
     * Code segment types.
     */
    enum SegmentType {
        /** Line-based segment (N lines) */
        LINES,
        /** Function/method segment */
        FUNCTION,
        /** Class/interface segment */
        CLASS,
        /** File segment (entire file) */
        FILE,
        /** Custom segment */
        CUSTOM
    }
    
    /**
     * Segment metadata for additional context.
     */
    record SegmentMetadata(java.util.Map<String, Object> properties) {
        public static SegmentMetadata empty() {
            return new SegmentMetadata(java.util.Map.of());
        }
        
        public static SegmentMetadata of(String key, Object value) {
            return new SegmentMetadata(java.util.Map.of(key, value));
        }
        
        @SuppressWarnings("unchecked")
        public <T> T get(String key, T defaultValue) {
            return (T) properties.getOrDefault(key, defaultValue);
        }
    }
    
    /**
     * Analyzer validation result.
     */
    record AnalyzerValidationResult(
        boolean valid,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
        public static AnalyzerValidationResult success() {
            return new AnalyzerValidationResult(true, List.of(), List.of());
        }
        
        public static AnalyzerValidationResult invalid(String... errors) {
            return new AnalyzerValidationResult(false, List.of(errors), List.of());
        }
        
        public static AnalyzerValidationResult withWarnings(String... warnings) {
            return new AnalyzerValidationResult(true, List.of(), List.of(warnings));
        }
    }
    
    /**
     * Analyzer execution statistics.
     */
    record AnalyzerStats(
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        double avgProcessingTimeMs,
        java.time.Instant lastRunTime,
        String lastError
    ) {
        public static AnalyzerStats empty() {
            return new AnalyzerStats(0, 0, 0, 0.0, null, null);
        }
        
        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successfulCalls / totalCalls * 100 : 0.0;
        }
        
        public double getFailureRate() {
            return totalCalls > 0 ? (double) failedCalls / totalCalls * 100 : 0.0;
        }
    }
}
