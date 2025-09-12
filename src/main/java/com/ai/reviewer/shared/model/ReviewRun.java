package com.ai.reviewer.shared.model;

import java.time.Instant;
import java.util.List;

/**
 * Complete review run result containing all analysis data and metadata.
 * 
 * <p>Represents a complete execution of the AI code review system for a specific
 * pull/merge request. This is the top-level aggregation record that contains
 * all findings, scores, statistics, and generated artifacts from the review process.
 * 
 * <p>Each ReviewRun is uniquely identified and contains comprehensive information
 * about the review execution, making it suitable for storage, retrieval, and
 * historical analysis.
 *
 * @param runId Unique identifier for this review run (UUID recommended)
 * @param repo Repository information where the review was performed
 * @param pull Pull/merge request information that was reviewed
 * @param createdAt Timestamp when the review run was initiated
 * @param completedAt Timestamp when the review run was completed (null if still running)
 * @param providerKeys List of AI/analysis provider identifiers used (e.g., ["gpt4o", "claude35"])
 * @param stats Execution statistics and performance metrics
 * @param findings Complete list of all findings discovered during the review
 * @param scores Calculated quality scores across all dimensions
 * @param artifacts File paths and references to generated reports and artifacts
 */
public record ReviewRun(
    String runId,
    RepoRef repo,
    PullRef pull,
    Instant createdAt,
    List<String> providerKeys,
    Stats stats,
    List<Finding> findings,
    Scores scores,
    Artifacts artifacts
) {
    /**
     * Get the repository.
     * @return repository reference
     */
    public RepoRef repository() {
        return repo;
    }
    
    /**
     * Get the pull request.
     * @return pull request reference
     */
    public PullRef pullRequest() {
        return pull;
    }
    
    /**
     * Get the start time.
     * @return start time
     */
    public Instant startTime() {
        return createdAt;
    }
    
    /**
     * Get the end time.
     * @return end time calculated from start time and latency
     */
    public Instant endTime() {
        if (stats != null && createdAt != null) {
            return createdAt.plusMillis(stats.latencyMs());
        }
        return null;
    }
    /**
     * Review run execution statistics and performance metrics.
     * 
     * <p>Contains quantitative data about the review execution,
     * including change statistics and performance measurements.
     * This information is useful for monitoring system performance
     * and understanding the scope of changes being reviewed.
     *
     * @param filesChanged Number of files that were modified in the pull/merge request
     * @param linesAdded Total number of lines added across all files
     * @param linesDeleted Total number of lines deleted/removed across all files
     * @param latencyMs Total execution time for the review in milliseconds
     * @param tokenCostUsd Estimated cost in USD for API tokens used (null if not calculated)
     */
    public record Stats(
        int filesChanged,
        int linesAdded,
        int linesDeleted,
        long latencyMs,
        Double tokenCostUsd
    ) {
        /**
         * Get processing time in milliseconds.
         * @return processing time in milliseconds
         */
        public long processingTimeMs() {
            return latencyMs;
        }
        
        /**
         * Get total files (alias for filesChanged).
         * @return total files
         */
        public int totalFiles() {
            return filesChanged;
        }
        
        /**
         * Get analyzed files (alias for filesChanged).
         * @return analyzed files
         */
        public int analyzedFiles() {
            return filesChanged;
        }
        
        /**
         * Get total findings (placeholder).
         * @return 0 for now
         */
        public int totalFindings() {
            return 0;
        }
    }

    /**
     * Generated artifacts and report file paths.
     * 
     * <p>Contains file system paths to all generated reports and artifacts
     * created during the review process. These files provide different
     * formats and views of the review results for various consumption
     * scenarios (CI/CD integration, human reading, tool processing).
     *
     * @param sarifPath File path to the SARIF (Static Analysis Results Interchange Format) report
     * @param reportMdPath File path to the Markdown formatted human-readable report
     * @param reportHtmlPath File path to the HTML formatted report for web viewing
     * @param reportPdfPath File path to the PDF formatted report for sharing/archival
     */
    public record Artifacts(
        String sarifPath,
        String reportMdPath,
        String reportHtmlPath,
        String reportPdfPath
    ) {
        /**
         * Get the SARIF report path.
         * @return SARIF report path
         */
        public String reportSarif() {
            return sarifPath;
        }
        
        /**
         * Get the Markdown report path.
         * @return Markdown report path
         */
        public String reportMarkdown() {
            return reportMdPath;
        }
        
        /**
         * Get the HTML report path.
         * @return HTML report path
         */
        public String reportHtml() {
            return reportHtmlPath;
        }
        
        /**
         * Get the JSON report path.
         * @return JSON report path (placeholder, returns null)
         */
        public String reportJson() {
            return null;
        }
    }
}
