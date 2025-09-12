package com.ai.reviewer.backend.domain.orchestrator.report;

import com.ai.reviewer.backend.domain.orchestrator.scoring.ScoringEngine;
import com.ai.reviewer.shared.model.*;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-format report generator for code review results.
 * 
 * <p>Supports generating reports in multiple formats:
 * - JSON: Machine-readable structured data
 * - Markdown: Human-readable documentation format
 * - HTML: Rich web-based presentation
 * - SARIF: Static Analysis Results Interchange Format (industry standard)
 */
@Component
public class ReportGenerator {
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ReportGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generate report in specified format.
     * 
     * @param reviewRun review run data
     * @param format target report format
     * @param config report generation configuration
     * @return generated report content
     */
    public String generateReport(ReviewRun reviewRun, ReportFormat format, ReportConfig config) {
        return switch (format) {
            case JSON -> generateJsonReport(reviewRun, config);
            case MARKDOWN -> generateMarkdownReport(reviewRun, config);
            case HTML -> generateHtmlReport(reviewRun, config);
            case SARIF -> generateSarifReport(reviewRun, config);
        };
    }
    
    /**
     * Generate JSON report.
     */
    private String generateJsonReport(ReviewRun reviewRun, ReportConfig config) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            
            // Report metadata
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("version", config.reportVersion());
            metadata.put("generatedAt", Instant.now().toString());
            metadata.put("generator", "AI Code Reviewer");
            root.set("metadata", metadata);
            
            // Review run info
            ObjectNode runInfo = objectMapper.createObjectNode();
            runInfo.put("id", reviewRun.runId());
            runInfo.put("repository", reviewRun.repository().owner() + "/" + reviewRun.repository().name());
            runInfo.put("pullRequest", reviewRun.pullRequest().number());
            runInfo.put("startTime", reviewRun.startTime().toString());
            if (reviewRun.endTime() != null) {
                runInfo.put("endTime", reviewRun.endTime().toString());
                runInfo.put("durationMs", reviewRun.endTime().toEpochMilli() - reviewRun.startTime().toEpochMilli());
            }
            root.set("runInfo", runInfo);
            
            // Statistics
            if (reviewRun.stats() != null) {
                ObjectNode stats = objectMapper.createObjectNode();
                stats.put("totalFiles", reviewRun.stats().totalFiles());
                stats.put("analyzedFiles", reviewRun.stats().analyzedFiles());
                stats.put("totalFindings", reviewRun.stats().totalFindings());
                stats.put("processingTimeMs", reviewRun.stats().processingTimeMs());
                root.set("statistics", stats);
            }
            
            // Scores
            if (reviewRun.scores() != null) {
                ObjectNode scores = objectMapper.createObjectNode();
                scores.put("totalScore", reviewRun.scores().totalScore());
                
                ObjectNode dimensions = objectMapper.createObjectNode();
                reviewRun.scores().dimensions().forEach((key, value) -> dimensions.put(key.name(), value));
                scores.set("dimensions", dimensions);
                
                ObjectNode weights = objectMapper.createObjectNode();
                reviewRun.scores().weights().forEach((key, value) -> weights.put(key.name(), value));
                scores.set("weights", weights);
                
                root.set("scores", scores);
            }
            
            // Findings
            if (config.includeFindings() && reviewRun.findings() != null) {
                ArrayNode findings = objectMapper.createArrayNode();
                for (Finding finding : reviewRun.findings()) {
                    ObjectNode findingNode = createFindingJsonNode(finding);
                    findings.add(findingNode);
                }
                root.set("findings", findings);
            }
            
            // Artifacts (if present)
            if (config.includeArtifacts() && reviewRun.artifacts() != null) {
                ObjectNode artifacts = objectMapper.createObjectNode();
                if (reviewRun.artifacts().reportJson() != null) {
                    artifacts.put("reportJson", reviewRun.artifacts().reportJson());
                }
                if (reviewRun.artifacts().reportMarkdown() != null) {
                    artifacts.put("reportMarkdown", reviewRun.artifacts().reportMarkdown());
                }
                if (reviewRun.artifacts().reportHtml() != null) {
                    artifacts.put("reportHtml", reviewRun.artifacts().reportHtml());
                }
                if (reviewRun.artifacts().reportSarif() != null) {
                    artifacts.put("reportSarif", reviewRun.artifacts().reportSarif());
                }
                root.set("artifacts", artifacts);
            }
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate JSON report", e);
        }
    }
    
    /**
     * Generate Markdown report.
     */
    private String generateMarkdownReport(ReviewRun reviewRun, ReportConfig config) {
        StringBuilder md = new StringBuilder();
        
        // Title and metadata
        md.append("# Code Review Report\n\n");
        md.append("**Repository:** ").append(reviewRun.repository().owner())
          .append("/").append(reviewRun.repository().name()).append("\n");
        md.append("**Pull Request:** #").append(reviewRun.pullRequest().number()).append("\n");
        md.append("**Generated:** ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n\n");
        
        // Summary scores
        if (reviewRun.scores() != null) {
            md.append("## Quality Scores\n\n");
            md.append("**Overall Score:** ").append(String.format("%.1f/100", reviewRun.scores().totalScore())).append("\n\n");
            
            md.append("| Dimension | Score | Weight |\n");
            md.append("|-----------|-------|--------|\n");
            
            for (Dimension dim : Dimension.values()) {
                Double score = reviewRun.scores().dimensions().get(dim);
                Double weight = reviewRun.scores().weights().get(dim);
                if (score != null && weight != null) {
                    md.append("| ").append(formatDimensionName(dim))
                      .append(" | ").append(String.format("%.1f", score))
                      .append(" | ").append(String.format("%.1f%%", weight * 100))
                      .append(" |\n");
                }
            }
            md.append("\n");
        }
        
        // Statistics
        if (reviewRun.stats() != null) {
            md.append("## Statistics\n\n");
            md.append("- **Files Analyzed:** ").append(reviewRun.stats().analyzedFiles())
              .append(" of ").append(reviewRun.stats().totalFiles()).append("\n");
            md.append("- **Total Findings:** ").append(reviewRun.stats().totalFindings()).append("\n");
            md.append("- **Processing Time:** ").append(reviewRun.stats().processingTimeMs()).append(" ms\n\n");
        }
        
        // Findings summary
        if (config.includeFindings() && reviewRun.findings() != null && !reviewRun.findings().isEmpty()) {
            md.append("## Findings Summary\n\n");
            
            Map<Severity, Long> bySeverity = reviewRun.findings().stream()
                .collect(Collectors.groupingBy(Finding::severity, Collectors.counting()));
            
            md.append("| Severity | Count |\n");
            md.append("|----------|-------|\n");
            for (Severity severity : Severity.values()) {
                long count = bySeverity.getOrDefault(severity, 0L);
                md.append("| ").append(severity.name())
                  .append(" | ").append(count).append(" |\n");
            }
            md.append("\n");
            
            // Detailed findings
            if (config.includeDetailedFindings()) {
                md.append("## Detailed Findings\n\n");
                
                Map<Dimension, List<Finding>> byDimension = reviewRun.findings().stream()
                    .collect(Collectors.groupingBy(Finding::dimension));
                
                for (Dimension dimension : Dimension.values()) {
                    List<Finding> dimensionFindings = byDimension.get(dimension);
                    if (dimensionFindings != null && !dimensionFindings.isEmpty()) {
                        md.append("### ").append(formatDimensionName(dimension)).append("\n\n");
                        
                        for (Finding finding : dimensionFindings) {
                            md.append("#### ").append(finding.severity().name())
                              .append(" - ").append(finding.file());
                            if (finding.line() != null) {
                                md.append(":").append(finding.line());
                            }
                            md.append("\n\n");
                            
                            md.append(finding.message()).append("\n\n");
                            
                            if (!finding.sources().isEmpty()) {
                                md.append("**Sources:** ").append(String.join(", ", finding.sources())).append("\n");
                            }
                            md.append("**Confidence:** ").append(String.format("%.1f%%", finding.confidence() * 100)).append("\n\n");
                            md.append("---\n\n");
                        }
                    }
                }
            }
        }
        
        return md.toString();
    }
    
    /**
     * Generate HTML report.
     */
    private String generateHtmlReport(ReviewRun reviewRun, ReportConfig config) {
        StringBuilder html = new StringBuilder();
        
        // HTML document structure
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<title>Code Review Report</title>\n");
        html.append("<style>\n").append(getDefaultCss()).append("\n</style>\n");
        html.append("</head>\n<body>\n");
        
        // Header
        html.append("<div class='header'>\n");
        html.append("<h1>Code Review Report</h1>\n");
        html.append("<div class='metadata'>\n");
        html.append("<span><strong>Repository:</strong> ").append(reviewRun.repository().owner())
            .append("/").append(reviewRun.repository().name()).append("</span>\n");
        html.append("<span><strong>PR:</strong> #").append(reviewRun.pullRequest().number()).append("</span>\n");
        html.append("<span><strong>Generated:</strong> ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("</span>\n");
        html.append("</div>\n</div>\n");
        
        // Score dashboard
        if (reviewRun.scores() != null) {
            html.append("<div class='score-dashboard'>\n");
            html.append("<h2>Quality Dashboard</h2>\n");
            
            double totalScore = reviewRun.scores().totalScore();
            String scoreClass = getScoreClass(totalScore);
            html.append("<div class='total-score ").append(scoreClass).append("'>\n");
            html.append("<span class='score-value'>").append(String.format("%.1f", totalScore)).append("</span>\n");
            html.append("<span class='score-label'>Overall Score</span>\n");
            html.append("</div>\n");
            
            html.append("<div class='dimension-scores'>\n");
            for (Dimension dim : Dimension.values()) {
                Double score = reviewRun.scores().dimensions().get(dim);
                if (score != null) {
                    String dimScoreClass = getScoreClass(score);
                    html.append("<div class='dimension-score ").append(dimScoreClass).append("'>\n");
                    html.append("<span class='score-value'>").append(String.format("%.1f", score)).append("</span>\n");
                    html.append("<span class='score-label'>").append(formatDimensionName(dim)).append("</span>\n");
                    html.append("</div>\n");
                }
            }
            html.append("</div>\n</div>\n");
        }
        
        // Statistics
        if (reviewRun.stats() != null) {
            html.append("<div class='statistics'>\n");
            html.append("<h2>Statistics</h2>\n");
            html.append("<div class='stat-grid'>\n");
            html.append("<div class='stat-item'><span class='stat-value'>").append(reviewRun.stats().analyzedFiles())
                .append("</span><span class='stat-label'>Files Analyzed</span></div>\n");
            html.append("<div class='stat-item'><span class='stat-value'>").append(reviewRun.stats().totalFindings())
                .append("</span><span class='stat-label'>Total Findings</span></div>\n");
            html.append("<div class='stat-item'><span class='stat-value'>").append(reviewRun.stats().processingTimeMs())
                .append("ms</span><span class='stat-label'>Processing Time</span></div>\n");
            html.append("</div>\n</div>\n");
        }
        
        // Findings
        if (config.includeFindings() && reviewRun.findings() != null && !reviewRun.findings().isEmpty()) {
            html.append("<div class='findings'>\n");
            html.append("<h2>Findings</h2>\n");
            
            // Group by dimension
            Map<Dimension, List<Finding>> byDimension = reviewRun.findings().stream()
                .collect(Collectors.groupingBy(Finding::dimension));
            
            for (Dimension dimension : Dimension.values()) {
                List<Finding> dimensionFindings = byDimension.get(dimension);
                if (dimensionFindings != null && !dimensionFindings.isEmpty()) {
                    html.append("<div class='dimension-section'>\n");
                    html.append("<h3>").append(formatDimensionName(dimension)).append("</h3>\n");
                    
                    for (Finding finding : dimensionFindings) {
                        String severityClass = finding.severity().name().toLowerCase();
                        html.append("<div class='finding ").append(severityClass).append("'>\n");
                        html.append("<div class='finding-header'>\n");
                        html.append("<span class='severity'>").append(finding.severity().name()).append("</span>\n");
                        html.append("<span class='location'>").append(finding.file());
                        if (finding.line() != null) {
                            html.append(":").append(finding.line());
                        }
                        html.append("</span>\n");
                        html.append("<span class='confidence'>").append(String.format("%.0f%%", finding.confidence() * 100)).append("</span>\n");
                        html.append("</div>\n");
                        html.append("<div class='finding-message'>").append(escapeHtml(finding.message())).append("</div>\n");
                        if (!finding.sources().isEmpty()) {
                            html.append("<div class='finding-sources'>Sources: ").append(String.join(", ", finding.sources())).append("</div>\n");
                        }
                        html.append("</div>\n");
                    }
                    html.append("</div>\n");
                }
            }
            html.append("</div>\n");
        }
        
        html.append("</body>\n</html>");
        return html.toString();
    }
    
    /**
     * Generate SARIF (Static Analysis Results Interchange Format) report.
     */
    private String generateSarifReport(ReviewRun reviewRun, ReportConfig config) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", "2.1.0");
            root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
            
            ArrayNode runs = objectMapper.createArrayNode();
            ObjectNode run = objectMapper.createObjectNode();
            
            // Tool information
            ObjectNode tool = objectMapper.createObjectNode();
            ObjectNode driver = objectMapper.createObjectNode();
            driver.put("name", "AI Code Reviewer");
            driver.put("version", config.reportVersion());
            driver.put("informationUri", "https://ai-reviewer.example.com");
            tool.set("driver", driver);
            run.set("tool", tool);
            
            // Results (findings)
            if (config.includeFindings() && reviewRun.findings() != null) {
                ArrayNode results = objectMapper.createArrayNode();
                
                for (Finding finding : reviewRun.findings()) {
                    ObjectNode result = objectMapper.createObjectNode();
                    
                    result.put("ruleId", generateRuleId(finding));
                    result.put("level", mapSeverityToSarifLevel(finding.severity()));
                    
                    ObjectNode message = objectMapper.createObjectNode();
                    message.put("text", finding.message());
                    result.set("message", message);
                    
                    // Location
                    ArrayNode locations = objectMapper.createArrayNode();
                    ObjectNode location = objectMapper.createObjectNode();
                    ObjectNode physicalLocation = objectMapper.createObjectNode();
                    
                    ObjectNode artifactLocation = objectMapper.createObjectNode();
                    artifactLocation.put("uri", finding.file());
                    physicalLocation.set("artifactLocation", artifactLocation);
                    
                    if (finding.line() != null) {
                        ObjectNode region = objectMapper.createObjectNode();
                        region.put("startLine", finding.line());
                        if (finding.endLine() != finding.line()) {
                            region.put("endLine", finding.endLine());
                        }
                        physicalLocation.set("region", region);
                    }
                    
                    location.set("physicalLocation", physicalLocation);
                    locations.add(location);
                    result.set("locations", locations);
                    
                    // Properties
                    ObjectNode properties = objectMapper.createObjectNode();
                    properties.put("confidence", finding.confidence());
                    properties.put("dimension", finding.dimension().name());
                    ArrayNode sources = objectMapper.createArrayNode();
                    finding.sources().forEach(sources::add);
                    properties.set("sources", sources);
                    result.set("properties", properties);
                    
                    results.add(result);
                }
                
                run.set("results", results);
            }
            
            // Properties
            ObjectNode properties = objectMapper.createObjectNode();
            properties.put("runId", reviewRun.runId());
            properties.put("repository", reviewRun.repository().owner() + "/" + reviewRun.repository().name());
            properties.put("pullRequest", reviewRun.pullRequest().number());
            if (reviewRun.scores() != null) {
                properties.put("totalScore", reviewRun.scores().totalScore());
            }
            run.set("properties", properties);
            
            runs.add(run);
            root.set("runs", runs);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate SARIF report", e);
        }
    }
    
    /**
     * Create JSON node for a finding.
     */
    private ObjectNode createFindingJsonNode(Finding finding) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("file", finding.file());
        if (finding.line() != null && finding.line() > 0) {
            node.put("line", finding.line());
        }
        if (finding.endLine() > 0) {
            node.put("endLine", finding.endLine());
        }
        node.put("message", finding.message());
        node.put("severity", finding.severity().name());
        node.put("dimension", finding.dimension().name());
        
        ArrayNode sources = objectMapper.createArrayNode();
        finding.sources().forEach(sources::add);
        node.set("sources", sources);
        
        node.put("confidence", finding.confidence());
        return node;
    }
    
    /**
     * Format dimension name for display.
     */
    private String formatDimensionName(Dimension dimension) {
        return switch (dimension) {
            case SECURITY -> "Security";
            case QUALITY -> "Code Quality";
            case MAINTAINABILITY -> "Maintainability";
            case PERFORMANCE -> "Performance";
            case TEST_COVERAGE -> "Test Coverage";
        };
    }
    
    /**
     * Get CSS class based on score.
     */
    private String getScoreClass(double score) {
        if (score >= 90) return "excellent";
        if (score >= 70) return "good";
        if (score >= 50) return "fair";
        return "poor";
    }
    
    /**
     * Generate rule ID for SARIF.
     */
    private String generateRuleId(Finding finding) {
        return finding.dimension().name().toLowerCase() + "." + 
               finding.severity().name().toLowerCase() + "." +
               Math.abs(finding.message().hashCode() % 1000);
    }
    
    /**
     * Map severity to SARIF level.
     */
    private String mapSeverityToSarifLevel(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "error";
            case MAJOR -> "error";
            case MINOR -> "warning";
            case INFO -> "note";
        };
    }
    
    /**
     * Escape HTML characters.
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
    
    /**
     * Get default CSS for HTML reports.
     */
    private String getDefaultCss() {
        return """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
            .header { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .header h1 { margin: 0 0 10px 0; color: #333; }
            .metadata { display: flex; gap: 20px; flex-wrap: wrap; }
            .metadata span { color: #666; }
            .score-dashboard { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .total-score { text-align: center; margin-bottom: 20px; padding: 20px; border-radius: 8px; }
            .total-score .score-value { display: block; font-size: 3em; font-weight: bold; line-height: 1; }
            .total-score .score-label { display: block; font-size: 1.2em; margin-top: 5px; }
            .dimension-scores { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }
            .dimension-score { text-align: center; padding: 15px; border-radius: 8px; }
            .dimension-score .score-value { display: block; font-size: 2em; font-weight: bold; }
            .dimension-score .score-label { display: block; margin-top: 5px; }
            .excellent { background-color: #d4edda; color: #155724; }
            .good { background-color: #d1ecf1; color: #0c5460; }
            .fair { background-color: #fff3cd; color: #856404; }
            .poor { background-color: #f8d7da; color: #721c24; }
            .statistics { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; }
            .stat-item { text-align: center; padding: 15px; background: #f8f9fa; border-radius: 6px; }
            .stat-value { display: block; font-size: 2em; font-weight: bold; color: #495057; }
            .stat-label { display: block; margin-top: 5px; color: #6c757d; }
            .findings { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .dimension-section { margin-bottom: 30px; }
            .dimension-section h3 { color: #495057; border-bottom: 2px solid #dee2e6; padding-bottom: 10px; }
            .finding { margin-bottom: 15px; padding: 15px; border-radius: 6px; border-left: 4px solid; }
            .finding.critical { border-color: #dc3545; background-color: #f8d7da; }
            .finding.major { border-color: #fd7e14; background-color: #ffeaa7; }
            .finding.minor { border-color: #ffc107; background-color: #fff3cd; }
            .finding.info { border-color: #17a2b8; background-color: #d1ecf1; }
            .finding-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
            .severity { font-weight: bold; padding: 4px 8px; border-radius: 4px; color: white; }
            .finding.critical .severity { background-color: #dc3545; }
            .finding.major .severity { background-color: #fd7e14; }
            .finding.minor .severity { background-color: #ffc107; color: #212529; }
            .finding.info .severity { background-color: #17a2b8; }
            .location { font-family: Monaco, 'Courier New', monospace; background: rgba(0,0,0,0.05); padding: 2px 6px; border-radius: 3px; }
            .confidence { font-size: 0.9em; color: #6c757d; }
            .finding-message { margin-bottom: 10px; line-height: 1.5; }
            .finding-sources { font-size: 0.9em; color: #6c757d; }
            """;
    }
    
    /**
     * Report format enumeration.
     */
    public enum ReportFormat {
        JSON, MARKDOWN, HTML, SARIF
    }
    
    /**
     * Report generation configuration.
     */
    public record ReportConfig(
        String reportVersion,
        boolean includeFindings,
        boolean includeDetailedFindings,
        boolean includeArtifacts,
        boolean includeStatistics,
        String customCss,
        Map<String, Object> customProperties
    ) {
        public static ReportConfig defaultConfig() {
            return new ReportConfig(
                "1.0.0",    // reportVersion
                true,       // includeFindings
                true,       // includeDetailedFindings
                false,      // includeArtifacts
                true,       // includeStatistics
                null,       // customCss
                Map.of()    // customProperties
            );
        }
        
        public static ReportConfig summary() {
            return new ReportConfig(
                "1.0.0",
                true,       // includeFindings
                false,      // includeDetailedFindings (summary only)
                false,
                true,
                null,
                Map.of()
            );
        }
        
        public static ReportConfig detailed() {
            return new ReportConfig(
                "1.0.0",
                true,
                true,       // includeDetailedFindings
                true,       // includeArtifacts
                true,
                null,
                Map.of()
            );
        }
    }
    
    /**
     * Report generation exception.
     */
    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
