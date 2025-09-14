package com.ai.reviewer.backend.web.dto;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 评审运行DTO for web interface.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewRunDto(
    String runId,
    RepoInfo repo,
    PullInfo pull,
    Instant createdAt,
    List<String> providerKeys,
    StatsInfo stats,
    List<FindingDto> findings,
    ScoresInfo scores,
    ArtifactsInfo artifacts,
    DiffInfo diffInfo
) {
    
    // 兼容性构造器（不包含diff信息）
    public ReviewRunDto(String runId, RepoInfo repo, PullInfo pull, Instant createdAt,
                       List<String> providerKeys, StatsInfo stats, 
                       List<FindingDto> findings, ScoresInfo scores,
                       ArtifactsInfo artifacts) {
        this(runId, repo, pull, createdAt, providerKeys, stats, findings, scores, artifacts, null);
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepoInfo(
        String owner,
        String name
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullInfo(
        String number,
        String title,
        String sourceBranch,
        String targetBranch
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatsInfo(
        int filesChanged,
        int linesAdded,
        int linesDeleted,
        long latencyMs,
        Double tokenCostUsd
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FindingDto(
        String id,
        String file,
        int startLine,
        int endLine,
        Severity severity,
        Dimension dimension,
        String title,
        String evidence,
        String suggestion,
        String patch,
        List<String> sources,
        double confidence
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoresInfo(
        double totalScore,
        Map<Dimension, Double> dimensions,
        Map<Dimension, Double> weights
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtifactsInfo(
        String sarifPath,
        String reportMdPath,
        String reportHtmlPath,
        String reportPdfPath
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiffInfo(
        String content,
        List<FileChange> files
    ) {
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record FileChange(
            String filename,
            String status,
            int additions,
            int deletions,
            int changes,
            String patch
        ) {}
    }
}
