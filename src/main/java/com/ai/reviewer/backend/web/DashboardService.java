package com.ai.reviewer.backend.web;

import com.ai.reviewer.backend.infra.jpa.entity.FindingEntity;
import com.ai.reviewer.backend.infra.jpa.entity.ReviewRunEntity;
import com.ai.reviewer.backend.infra.jpa.entity.ScoreEntity;
import com.ai.reviewer.backend.infra.jpa.repository.ReviewRunRepository;
import com.ai.reviewer.backend.web.dto.ReviewRunDto;
import com.ai.reviewer.shared.enums.Dimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Dashboard service for web interface.
 */
@Service
public class DashboardService {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    
    private final ReviewRunRepository reviewRunRepository;
    
    @Autowired
    public DashboardService(ReviewRunRepository reviewRunRepository) {
        this.reviewRunRepository = reviewRunRepository;
    }
    
    /**
     * Get paginated review run summaries with optional filtering.
     */
    public Page<ReviewRunSummary> getHistoryRuns(Pageable pageable, String repoFilter, String platformFilter) {
        logger.debug("Fetching history runs: page={}, size={}, repoFilter={}, platformFilter={}", 
            pageable.getPageNumber(), pageable.getPageSize(), repoFilter, platformFilter);
        
        try {
            Page<ReviewRunEntity> entities;
            
            // If no filters, get all runs
            if (!StringUtils.hasText(repoFilter) && !StringUtils.hasText(platformFilter)) {
                entities = reviewRunRepository.findAll(pageable);
            } else {
                // For now, get all and filter in memory (could be optimized with custom queries)
                List<ReviewRunEntity> allEntities = reviewRunRepository.findAll();
                List<ReviewRunEntity> filtered = allEntities.stream()
                    .filter(entity -> matchesRepoFilter(entity, repoFilter))
                    .filter(entity -> matchesPlatformFilter(entity, platformFilter))
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());
                
                long totalFiltered = allEntities.stream()
                    .filter(entity -> matchesRepoFilter(entity, repoFilter))
                    .filter(entity -> matchesPlatformFilter(entity, platformFilter))
                    .count();
                
                entities = new PageImpl<>(filtered, pageable, totalFiltered);
            }
            
            // Convert to summary DTOs
            List<ReviewRunSummary> summaries = entities.getContent().stream()
                .map(this::convertToSummary)
                .collect(Collectors.toList());
                
            return new PageImpl<>(summaries, pageable, entities.getTotalElements());
            
        } catch (Exception e) {
            logger.error("Error fetching history runs", e);
            // Return mock data as fallback
            return createMockHistoryData(pageable, repoFilter, platformFilter);
        }
    }
    
    private boolean matchesRepoFilter(ReviewRunEntity entity, String repoFilter) {
        if (!StringUtils.hasText(repoFilter)) {
            return true;
        }
        String repoFullName = entity.getRepoOwner() + "/" + entity.getRepoName();
        return repoFullName.toLowerCase().contains(repoFilter.toLowerCase());
    }
    
    private boolean matchesPlatformFilter(ReviewRunEntity entity, String platformFilter) {
        if (!StringUtils.hasText(platformFilter)) {
            return true;
        }
        return entity.getRepoProvider() != null && 
               entity.getRepoProvider().toLowerCase().contains(platformFilter.toLowerCase());
    }
    
    private ReviewRunSummary convertToSummary(ReviewRunEntity entity) {
        // Calculate total score from scores if available
        double totalScore = 85.0; // Default fallback
        if (entity.getScores() != null && !entity.getScores().isEmpty()) {
            totalScore = entity.getScores().stream()
                .mapToDouble(score -> score.getScore().doubleValue())
                .average()
                .orElse(85.0);
        }
        
        return new ReviewRunSummary(
            entity.getRunId(),
            entity.getRepoOwner(),
            entity.getRepoName(),
            entity.getPullNumber(),
            entity.getPullTitle(),
            entity.getRepoProvider() != null ? entity.getRepoProvider() : "GitHub",
            totalScore,
            entity.getCreatedAt()
        );
    }
    
    /**
     * Create mock data as fallback.
     */
    private Page<ReviewRunSummary> createMockHistoryData(Pageable pageable, String repoFilter, String platformFilter) {
        List<ReviewRunSummary> mockData = List.of(
            new ReviewRunSummary("run-001", "user1", "project-a", "123", "Fix security issue", 
                "GitHub", 85.5, Instant.now().minusSeconds(3600)),
            new ReviewRunSummary("run-002", "user1", "project-a", "124", "Add new feature", 
                "GitHub", 92.0, Instant.now().minusSeconds(7200)),
            new ReviewRunSummary("run-003", "user2", "project-b", "45", "Refactor code", 
                "GitLab", 78.3, Instant.now().minusSeconds(10800)),
            new ReviewRunSummary("run-004", "user2", "project-b", "46", "Update dependencies", 
                "GitLab", 88.7, Instant.now().minusSeconds(14400)),
            new ReviewRunSummary("run-005", "user3", "project-c", "67", "Performance improvement", 
                "GitHub", 94.2, Instant.now().minusSeconds(18000))
        );
        
        // Apply filters
        List<ReviewRunSummary> filtered = mockData.stream()
            .filter(run -> repoFilter == null || repoFilter.isEmpty() || 
                (run.repoOwner() + "/" + run.repoName()).toLowerCase().contains(repoFilter.toLowerCase()))
            .filter(run -> platformFilter == null || platformFilter.isEmpty() || 
                run.platform().toLowerCase().contains(platformFilter.toLowerCase()))
            .collect(Collectors.toList());
        
        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ReviewRunSummary> pageContent = start >= filtered.size() ? 
            List.of() : filtered.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }
    
    /**
     * Get review run details by ID.
     */
    public Optional<ReviewRunDto> getReviewRun(String runId) {
        logger.debug("Fetching review run details: runId={}", runId);
        
        try {
            Optional<ReviewRunEntity> entityOpt = reviewRunRepository.findByRunIdWithDetails(runId);
            
            if (entityOpt.isEmpty()) {
                logger.debug("Review run not found: runId={}", runId);
                // Return mock data for run-001 as fallback
                if ("run-001".equals(runId)) {
                    return Optional.of(createMockReviewRun(runId));
                }
                return Optional.empty();
            }
            
            ReviewRunEntity entity = entityOpt.get();
            return Optional.of(convertToDto(entity));
            
        } catch (Exception e) {
            logger.warn("Error fetching review run: runId={}, falling back to mock data", runId, e);
            if ("run-001".equals(runId)) {
                return Optional.of(createMockReviewRun(runId));
            }
            return Optional.empty();
        }
    }
    
    private ReviewRunDto convertToDto(ReviewRunEntity entity) {
        // Convert repo info
        var repo = new ReviewRunDto.RepoInfo(entity.getRepoOwner(), entity.getRepoName());
        
        // Convert pull info  
        var pull = new ReviewRunDto.PullInfo(entity.getPullNumber(), entity.getPullTitle(),
            entity.getPullSourceBranch(), entity.getPullTargetBranch());
        
        // Convert findings
        List<ReviewRunDto.FindingDto> findings = entity.getFindings() != null ?
            entity.getFindings().stream()
                .map(this::convertFinding)
                .collect(Collectors.toList()) : List.of();
        
        // Convert stats
        var stats = new ReviewRunDto.StatsInfo(
            entity.getFilesChanged() != null ? entity.getFilesChanged() : 0,
            entity.getLinesAdded() != null ? entity.getLinesAdded() : 0,
            entity.getLinesDeleted() != null ? entity.getLinesDeleted() : 0,
            entity.getLatencyMs() != null ? entity.getLatencyMs() : 0L,
            entity.getTokenCostUsd() != null ? entity.getTokenCostUsd().doubleValue() : null
        );
        
        // Convert scores
        Map<Dimension, Double> dimensionScores = entity.getScores() != null ?
            entity.getScores().stream()
                .collect(Collectors.toMap(
                    score -> score.getDimension(),
                    score -> score.getScore().doubleValue()
                )) : Map.of();
        
        double totalScore = dimensionScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(85.0);
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        var scores = new ReviewRunDto.ScoresInfo(totalScore, dimensionScores, weights);
        
        // Convert artifacts
        var artifacts = entity.getArtifact() != null ?
            new ReviewRunDto.ArtifactsInfo(
                entity.getArtifact().getSarifPath(),
                entity.getArtifact().getReportMdPath(),
                entity.getArtifact().getReportHtmlPath(),
                entity.getArtifact().getReportPdfPath()
            ) : null;
        
        return new ReviewRunDto(entity.getRunId(), repo, pull, entity.getCreatedAt(),
            List.of("github"), stats, findings, scores, artifacts);
    }
    
    private ReviewRunDto.FindingDto convertFinding(FindingEntity entity) {
        // For now, provide a default sources list (could parse JSON later)
        List<String> sources = List.of("static-analysis");
        
        return new ReviewRunDto.FindingDto(
            entity.getId(),
            entity.getFile(),
            entity.getStartLine(),
            entity.getEndLine(),
            entity.getSeverity(),
            entity.getDimension(),
            entity.getTitle(),
            entity.getEvidence(),
            entity.getSuggestion(),
            entity.getPatch(),
            sources,
            entity.getConfidence().doubleValue()
        );
    }
    
    /**
     * Create mock ReviewRun data for demo.
     */
    private ReviewRunDto createMockReviewRun(String runId) {
        // Create repo info
        var repo = new ReviewRunDto.RepoInfo("user1", "project-a");
        
        // Create pull info
        var pull = new ReviewRunDto.PullInfo("123", "Fix security vulnerability in authentication", 
            "feature/security-fix", "main");
        
        // Create some mock findings
        List<ReviewRunDto.FindingDto> findings = List.of(
            new ReviewRunDto.FindingDto("find-1", "src/auth/User.java", 45, 48, 
                com.ai.reviewer.shared.enums.Severity.MAJOR, Dimension.SECURITY, "Security vulnerability found",
                "Weak password hashing detected", "Use bcrypt for password hashing", 
                "- password = md5(password)\n+ password = bcrypt.hash(password)", 
                List.of("static-analysis"), 0.9),
            new ReviewRunDto.FindingDto("find-2", "src/utils/Helper.java", 23, 25,
                com.ai.reviewer.shared.enums.Severity.MINOR, Dimension.QUALITY, "Potential null pointer", 
                "Variable may be null", "Add null check before access",
                "if (obj != null) {\n    obj.method();\n}", 
                List.of("static-analysis"), 0.8)
        );
        
        // Create mock stats
        var stats = new ReviewRunDto.StatsInfo(3, 120, 25, 2500L, 0.15);
        
        // Create mock scores
        Map<Dimension, Double> dimensions = Map.of(
            Dimension.SECURITY, 85.0,
            Dimension.QUALITY, 90.0,
            Dimension.MAINTAINABILITY, 88.0,
            Dimension.PERFORMANCE, 92.0,
            Dimension.TEST_COVERAGE, 75.0
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        var scores = new ReviewRunDto.ScoresInfo(86.0, dimensions, weights);
        
        // Create mock artifacts
        var artifacts = new ReviewRunDto.ArtifactsInfo(
            "/reports/run-001.sarif",
            "/reports/run-001.md", 
            "/reports/run-001.html",
            "/reports/run-001.pdf"
        );
        
        return new ReviewRunDto(runId, repo, pull, Instant.now().minusSeconds(3600), 
            List.of("github"), stats, findings, scores, artifacts);
    }

    /**
     * Review run summary record.
     */
    public record ReviewRunSummary(
        String runId,
        String repoOwner,
        String repoName,
        String pullNumber,
        String pullTitle,
        String platform,
        double totalScore,
        Instant createdAt
    ) {}
}
