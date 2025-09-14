package com.ai.reviewer.backend.infra.jpa.mapper;

import com.ai.reviewer.backend.infra.jpa.entity.ReviewRunEntity;
import com.ai.reviewer.shared.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between ReviewRunEntity and ReviewRun record.
 * 
 * <p>Handles the conversion logic between JPA entities and domain records,
 * including JSON serialization/deserialization for complex fields.
 */
@Component
public class ReviewRunMapper {

    private final ObjectMapper objectMapper;
    private final FindingMapper findingMapper;
    private final ScoreMapper scoreMapper;
    private final ArtifactMapper artifactMapper;

    @Autowired
    public ReviewRunMapper(ObjectMapper objectMapper, 
                          FindingMapper findingMapper,
                          ScoreMapper scoreMapper, 
                          ArtifactMapper artifactMapper) {
        this.objectMapper = objectMapper;
        this.findingMapper = findingMapper;
        this.scoreMapper = scoreMapper;
        this.artifactMapper = artifactMapper;
    }

    /**
     * Convert ReviewRunEntity to ReviewRun record.
     *
     * @param entity the entity to convert
     * @return converted record
     */
    public ReviewRun toRecord(ReviewRunEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ReviewRun(
                entity.getRunId(),
                toRepoRef(entity),
                toPullRef(entity),
                entity.getCreatedAt(),
                // completedAt field removed from ReviewRun
                parseProviderKeys(entity.getProviderKeysJson()),
                toStats(entity),
                entity.getFindings().stream()
                        .map(findingMapper::toRecord)
                        .collect(Collectors.toList()),
                scoreMapper.toScoresRecord(entity.getScores()),
                artifactMapper.toRecord(entity.getArtifact())
        );
    }

    /**
     * Convert ReviewRunEntity to ReviewRun domain object (alias for toRecord).
     *
     * @param entity the entity to convert
     * @return converted domain object
     */
    public ReviewRun toDomain(ReviewRunEntity entity) {
        return toRecord(entity);
    }

    /**
     * Convert ReviewRun record to ReviewRunEntity.
     *
     * @param record the record to convert
     * @return converted entity
     */
    public ReviewRunEntity toEntity(ReviewRun record) {
        if (record == null) {
            return null;
        }

        ReviewRunEntity entity = new ReviewRunEntity();
        entity.setRunId(record.runId());
        
        // Repository information
        RepoRef repo = record.repo();
        entity.setRepoProvider(repo.provider());
        entity.setRepoOwner(repo.owner());
        entity.setRepoName(repo.name());
        entity.setRepoUrl(repo.url());

        // Pull request information
        PullRef pull = record.pull();
        entity.setPullId(pull.id());
        entity.setPullNumber(pull.number());
        entity.setPullSourceBranch(pull.sourceBranch());
        entity.setPullTargetBranch(pull.targetBranch());
        entity.setPullSha(pull.sha());
        entity.setPullDraft(pull.draft());

        // Metadata
        entity.setCreatedAt(record.createdAt());
        entity.setProviderKeysJson(serializeProviderKeys(record.providerKeys()));

        // Statistics
        ReviewRun.Stats stats = record.stats();
        entity.setFilesChanged(stats.filesChanged());
        entity.setLinesAdded(stats.linesAdded());
        entity.setLinesDeleted(stats.linesDeleted());
        entity.setLatencyMs(stats.latencyMs());
        if (stats.tokenCostUsd() != null) {
            entity.setTokenCostUsd(BigDecimal.valueOf(stats.tokenCostUsd()));
        }

        // Total score (with null check)
        if (record.scores() != null) {
            entity.setTotalScore(BigDecimal.valueOf(record.scores().totalScore()));
            
            // 🔧 Fix: 转换并设置维度评分
            List<com.ai.reviewer.backend.infra.jpa.entity.ScoreEntity> scoreEntities = 
                scoreMapper.toEntities(record.scores());
            // 设置关联关系
            scoreEntities.forEach(scoreEntity -> scoreEntity.setReviewRun(entity));
            entity.setScores(scoreEntities);
        } else {
            entity.setTotalScore(BigDecimal.ZERO);
        }

        // Set status (default to COMPLETED for imported records)
        entity.setStatus(ReviewRunEntity.ReviewRunStatus.COMPLETED);

        return entity;
    }

    /**
     * Update an existing entity with data from a record.
     *
     * @param entity the entity to update
     * @param record the record with new data
     */
    public void updateEntity(ReviewRunEntity entity, ReviewRun record) {
        if (entity == null || record == null) {
            return;
        }

        // Update mutable fields only
        ReviewRun.Stats stats = record.stats();
        entity.setFilesChanged(stats.filesChanged());
        entity.setLinesAdded(stats.linesAdded());
        entity.setLinesDeleted(stats.linesDeleted());
        entity.setLatencyMs(stats.latencyMs());
        if (stats.tokenCostUsd() != null) {
            entity.setTokenCostUsd(BigDecimal.valueOf(stats.tokenCostUsd()));
        }

        // Total score (with null check)
        if (record.scores() != null) {
            entity.setTotalScore(BigDecimal.valueOf(record.scores().totalScore()));
            
            // 🔧 Fix: 更新维度评分
            List<com.ai.reviewer.backend.infra.jpa.entity.ScoreEntity> scoreEntities = 
                scoreMapper.toEntities(record.scores());
            // 清除旧的scores并设置新的
            entity.getScores().clear();
            scoreEntities.forEach(scoreEntity -> scoreEntity.setReviewRun(entity));
            entity.getScores().addAll(scoreEntities);
        } else {
            entity.setTotalScore(BigDecimal.ZERO);
        }
        entity.setProviderKeysJson(serializeProviderKeys(record.providerKeys()));
    }

    // Helper methods for converting embedded objects

    private RepoRef toRepoRef(ReviewRunEntity entity) {
        return new RepoRef(
                entity.getRepoProvider(),
                entity.getRepoOwner(),
                entity.getRepoName(),
                entity.getRepoUrl()
        );
    }

    private PullRef toPullRef(ReviewRunEntity entity) {
        return new PullRef(
                entity.getPullId(),
                entity.getPullNumber(),
                entity.getPullTitle() != null ? entity.getPullTitle() : "Pull Request",
                entity.getPullSourceBranch(),
                entity.getPullTargetBranch(),
                entity.getPullSha(),
                entity.getPullDraft()
        );
    }

    private ReviewRun.Stats toStats(ReviewRunEntity entity) {
        Double tokenCostUsd = entity.getTokenCostUsd() != null 
                ? entity.getTokenCostUsd().doubleValue() 
                : null;

        return new ReviewRun.Stats(
                entity.getFilesChanged(),
                entity.getLinesAdded(),
                entity.getLinesDeleted(),
                entity.getLatencyMs(),
                tokenCostUsd
        );
    }

    // JSON serialization helpers

    private List<String> parseProviderKeys(String providerKeysJson) {
        if (providerKeysJson == null || providerKeysJson.isEmpty()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(providerKeysJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse provider keys JSON", e);
        }
    }

    private String serializeProviderKeys(List<String> providerKeys) {
        if (providerKeys == null || providerKeys.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(providerKeys);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize provider keys", e);
        }
    }

    /**
     * Convert a lightweight ReviewRunEntity (without relationships) to a summary record.
     *
     * @param entity the entity to convert
     * @return summary record with minimal data
     */
    public ReviewRunSummary toSummaryRecord(ReviewRunEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ReviewRunSummary(
                entity.getRunId(),
                toRepoRef(entity),
                toPullRef(entity),
                entity.getCreatedAt(),
                entity.getStatus().name(),
                entity.getTotalScore() != null ? entity.getTotalScore().doubleValue() : 0.0,
                parseProviderKeys(entity.getProviderKeysJson())
        );
    }

    /**
     * Simple summary record for list views and API responses.
     */
    public record ReviewRunSummary(
            String runId,
            RepoRef repo,
            PullRef pull,
            java.time.Instant createdAt,
            String status,
            double totalScore,
            List<String> providerKeys
    ) {}
}
