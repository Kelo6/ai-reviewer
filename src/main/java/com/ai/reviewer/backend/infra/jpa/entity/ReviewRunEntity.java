package com.ai.reviewer.backend.infra.jpa.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity for review_run table.
 * 
 * <p>Represents a complete AI code review execution with all associated
 * metadata, statistics, and relationships to findings, scores, and artifacts.
 */
@Entity
@Table(name = "review_run", indexes = {
    @Index(name = "idx_review_run_repo_pull_time", 
           columnList = "repo_owner, repo_name, pull_number, created_at"),
    @Index(name = "idx_review_run_status", 
           columnList = "status, created_at"),
    @Index(name = "idx_review_run_provider", 
           columnList = "repo_provider, created_at"),
    @Index(name = "idx_review_run_created_at", 
           columnList = "created_at")
})
public class ReviewRunEntity {

    @Id
    @Column(name = "run_id", length = 255)
    @NotBlank
    @Size(max = 255)
    private String runId;

    // Repository information (embedded from RepoRef)
    @Column(name = "repo_provider", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String repoProvider;

    @Column(name = "repo_owner", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String repoOwner;

    @Column(name = "repo_name", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String repoName;

    @Column(name = "repo_url", length = 500, nullable = false)
    @NotBlank
    @Size(max = 500)
    private String repoUrl;

    // Pull request information (embedded from PullRef)
    @Column(name = "pull_id", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String pullId;

    @Column(name = "pull_number", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String pullNumber;

    @Column(name = "pull_title", length = 500)
    @Size(max = 500)
    private String pullTitle;

    @Column(name = "pull_source_branch", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String pullSourceBranch;

    @Column(name = "pull_target_branch", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String pullTargetBranch;

    @Column(name = "pull_sha", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String pullSha;

    @Column(name = "pull_draft", nullable = false)
    private Boolean pullDraft = false;

    // Execution metadata
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    @NotNull
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private ReviewRunStatus status = ReviewRunStatus.PENDING;

    @Column(name = "provider_keys", columnDefinition = "JSON")
    private String providerKeysJson;

    // Statistics
    @Column(name = "files_changed", nullable = false)
    @PositiveOrZero
    private Integer filesChanged = 0;

    @Column(name = "lines_added", nullable = false)
    @PositiveOrZero
    private Integer linesAdded = 0;

    @Column(name = "lines_deleted", nullable = false)
    @PositiveOrZero
    private Integer linesDeleted = 0;

    @Column(name = "latency_ms", nullable = false)
    @PositiveOrZero
    private Long latencyMs = 0L;

    @Column(name = "token_cost_usd", precision = 9, scale = 4)
    private BigDecimal tokenCostUsd;

    // Computed scores
    @Column(name = "total_score", precision = 5, scale = 2)
    @PositiveOrZero
    private BigDecimal totalScore = BigDecimal.ZERO;

    // Relationships
    @OneToMany(mappedBy = "reviewRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FindingEntity> findings = new ArrayList<>();

    @OneToMany(mappedBy = "reviewRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ScoreEntity> scores = new ArrayList<>();

    @OneToOne(mappedBy = "reviewRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ArtifactEntity artifact;

    // Constructors
    public ReviewRunEntity() {}

    public ReviewRunEntity(String runId) {
        this.runId = runId;
    }

    // Getters and setters
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRepoProvider() { return repoProvider; }
    public void setRepoProvider(String repoProvider) { this.repoProvider = repoProvider; }

    public String getRepoOwner() { return repoOwner; }
    public void setRepoOwner(String repoOwner) { this.repoOwner = repoOwner; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getPullId() { return pullId; }
    public void setPullId(String pullId) { this.pullId = pullId; }

    public String getPullNumber() { return pullNumber; }
    public void setPullNumber(String pullNumber) { this.pullNumber = pullNumber; }

    public String getPullTitle() { return pullTitle; }
    public void setPullTitle(String pullTitle) { this.pullTitle = pullTitle; }

    public String getPullSourceBranch() { return pullSourceBranch; }
    public void setPullSourceBranch(String pullSourceBranch) { this.pullSourceBranch = pullSourceBranch; }

    public String getPullTargetBranch() { return pullTargetBranch; }
    public void setPullTargetBranch(String pullTargetBranch) { this.pullTargetBranch = pullTargetBranch; }

    public String getPullSha() { return pullSha; }
    public void setPullSha(String pullSha) { this.pullSha = pullSha; }

    public Boolean getPullDraft() { return pullDraft; }
    public void setPullDraft(Boolean pullDraft) { this.pullDraft = pullDraft; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public ReviewRunStatus getStatus() { return status; }
    public void setStatus(ReviewRunStatus status) { this.status = status; }

    public String getProviderKeysJson() { return providerKeysJson; }
    public void setProviderKeysJson(String providerKeysJson) { this.providerKeysJson = providerKeysJson; }

    public Integer getFilesChanged() { return filesChanged; }
    public void setFilesChanged(Integer filesChanged) { this.filesChanged = filesChanged; }

    public Integer getLinesAdded() { return linesAdded; }
    public void setLinesAdded(Integer linesAdded) { this.linesAdded = linesAdded; }

    public Integer getLinesDeleted() { return linesDeleted; }
    public void setLinesDeleted(Integer linesDeleted) { this.linesDeleted = linesDeleted; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public BigDecimal getTokenCostUsd() { return tokenCostUsd; }
    public void setTokenCostUsd(BigDecimal tokenCostUsd) { this.tokenCostUsd = tokenCostUsd; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }

    public List<FindingEntity> getFindings() { return findings; }
    public void setFindings(List<FindingEntity> findings) { this.findings = findings; }

    public List<ScoreEntity> getScores() { return scores; }
    public void setScores(List<ScoreEntity> scores) { this.scores = scores; }

    public ArtifactEntity getArtifact() { return artifact; }
    public void setArtifact(ArtifactEntity artifact) { this.artifact = artifact; }

    // Helper methods for managing relationships
    public void addFinding(FindingEntity finding) {
        findings.add(finding);
        finding.setReviewRun(this);
    }

    public void removeFinding(FindingEntity finding) {
        findings.remove(finding);
        finding.setReviewRun(null);
    }

    public void addScore(ScoreEntity score) {
        scores.add(score);
        score.setReviewRun(this);
    }

    public void removeScore(ScoreEntity score) {
        scores.remove(score);
        score.setReviewRun(null);
    }

    // Alias methods for compatibility with service layer expectations
    public String getId() { return runId; }
    public void setId(String id) { this.runId = id; }

    public BigDecimal getScoreTotal() { return totalScore; }
    public void setScoreTotal(BigDecimal scoreTotal) { this.totalScore = scoreTotal; }

    public Instant getStartTime() { return createdAt; }
    public void setStartTime(Instant startTime) { this.createdAt = startTime; }

    public Instant getEndTime() { return completedAt; }
    public void setEndTime(Instant endTime) { this.completedAt = endTime; }

    /**
     * Review run status enumeration.
     */
    public enum ReviewRunStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
