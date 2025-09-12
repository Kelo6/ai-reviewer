package com.ai.reviewer.backend.api.dto;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 评审运行详情响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewRunResponse(
    String runId,
    RepoInfo repo,
    PullInfo pull,
    Instant createdAt,
    List<String> providerKeys,
    StatsInfo stats,
    List<FindingInfo> findings,
    ScoresInfo scores,
    ArtifactsInfo artifacts
) {
    
    /**
     * 仓库信息。
     */
    public record RepoInfo(
        String owner,
        String name
    ) {}
    
    /**
     * Pull Request信息。
     */
    public record PullInfo(
        String number,
        String title,
        String sourceBranch,
        String targetBranch
    ) {}
    
    /**
     * 统计信息。
     */
    public record StatsInfo(
        int filesChanged,
        int linesAdded,
        int linesDeleted,
        long latencyMs,
        Double tokenCostUsd
    ) {}
    
    /**
     * 发现的问题。
     */
    public record FindingInfo(
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
    
    /**
     * 评分信息。
     */
    public record ScoresInfo(
        double totalScore,
        Map<Dimension, Double> dimensions,
        Map<Dimension, Double> weights
    ) {}
    
    /**
     * 产物信息。
     */
    public record ArtifactsInfo(
        String sarifPath,
        String reportMdPath,
        String reportHtmlPath,
        String reportPdfPath
    ) {}
}
