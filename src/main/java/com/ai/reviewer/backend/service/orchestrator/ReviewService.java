package com.ai.reviewer.backend.service.orchestrator;

import com.ai.reviewer.backend.domain.orchestrator.ReviewOrchestrator;
import com.ai.reviewer.backend.infra.jpa.entity.ReviewRunEntity;
import com.ai.reviewer.backend.infra.jpa.mapper.ReviewRunMapper;
import com.ai.reviewer.backend.infra.jpa.repository.ReviewRunRepository;
import com.ai.reviewer.shared.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer for review orchestration with persistence and transaction management.
 * 
 * <p>This service provides the main entry point for code review operations,
 * handling both the orchestration process and atomic data persistence.
 * It ensures that each review run is saved atomically with all its associated data.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final ReviewOrchestrator orchestrator;
    private final ReviewRunRepository reviewRunRepository;
    private final ReviewRunMapper reviewRunMapper;
    
    @Autowired
    public ReviewService(@Lazy ReviewOrchestrator orchestrator,
                        ReviewRunRepository reviewRunRepository,
                        ReviewRunMapper reviewRunMapper) {
        this.orchestrator = orchestrator;
        this.reviewRunRepository = reviewRunRepository;
        this.reviewRunMapper = reviewRunMapper;
    }
    
    /**
     * Trigger asynchronous review process (simplified entry point for webhooks).
     * 
     * @param repo Repository reference
     * @param pull Pull request reference
     * @return CompletableFuture that completes when review is triggered
     */
    public CompletableFuture<Void> triggerReview(RepoRef repo, PullRef pull) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Triggering AI review for PR {}/{} #{}", 
                        repo.owner(), repo.name(), pull.number());
                
                // Use default configuration for webhook-triggered reviews
                ReviewOrchestrator.ReviewConfig config = ReviewOrchestrator.ReviewConfig.defaultConfig();
                String providerKey = repo.provider();
                
                // Execute the review asynchronously
                executeReview(repo, pull, providerKey, config);
                
                logger.info("AI review completed for PR {}/{} #{}", 
                        repo.owner(), repo.name(), pull.number());
                        
            } catch (Exception e) {
                logger.error("Failed to complete AI review for PR {}/{} #{}", 
                        repo.owner(), repo.name(), pull.number(), e);
            }
        });
    }
    
    /**
     * Execute complete review process with atomic persistence.
     * 
     * <p>This method orchestrates the entire review process and ensures that
     * all data is persisted atomically. If any part of the process fails,
     * the entire transaction is rolled back.
     * 
     * @param repository repository reference
     * @param pullRequest pull request reference  
     * @param providerKey SCM provider identifier
     * @param config review configuration
     * @return review run result with database persistence
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<ReviewRun> executeReview(RepoRef repository, PullRef pullRequest,
                                                     String providerKey, ReviewOrchestrator.ReviewConfig config) {
        logger.info("Starting review execution for {}/{}#{}", 
            repository.owner(), repository.name(), pullRequest.number());
        
        // Start the orchestration process
        CompletableFuture<ReviewRun> orchestrationFuture = 
            orchestrator.runReview(repository, pullRequest, providerKey, config);
        
        // Chain persistence after orchestration completes
        return orchestrationFuture.thenCompose(reviewRun -> {
            try {
                // Save the completed review run atomically
                ReviewRun savedReviewRun = saveReviewRunAtomic(reviewRun);
                logger.info("Review run {} saved successfully", reviewRun.runId());
                return CompletableFuture.completedFuture(savedReviewRun);
                
            } catch (Exception e) {
                logger.error("Failed to save review run {}", reviewRun.runId(), e);
                return CompletableFuture.failedFuture(
                    new ReviewPersistenceException("Failed to save review run: " + e.getMessage(), e));
            }
        }).exceptionally(ex -> {
            logger.error("Review execution failed for {}/{}#{}", 
                repository.owner(), repository.name(), pullRequest.number(), ex);
            
            // Attempt to save error state
            try {
                ReviewRun errorRun = createErrorReviewRun(repository, pullRequest, ex);
                saveReviewRunAtomic(errorRun);
            } catch (Exception saveEx) {
                logger.warn("Failed to save error review run", saveEx);
            }
            
            throw new RuntimeException("Review execution failed", ex);
        });
    }
    
    /**
     * Save review run and all associated data atomically.
     * 
     * <p>This method ensures that the entire review run with all its findings,
     * scores, and artifacts is saved in a single database transaction.
     * If any part fails, the entire transaction is rolled back.
     * 
     * @param reviewRun review run to save
     * @return saved review run with database IDs populated
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReviewRun saveReviewRunAtomic(ReviewRun reviewRun) {
        logger.debug("Saving review run {} with {} findings", 
            reviewRun.runId(), reviewRun.findings() != null ? reviewRun.findings().size() : 0);
        
        try {
            // Check if review run already exists
            Optional<ReviewRunEntity> existingEntity = reviewRunRepository.findByRunId(reviewRun.runId());
            
            ReviewRunEntity entity;
            if (existingEntity.isPresent()) {
                // Update existing entity
                entity = existingEntity.get();
                reviewRunMapper.updateEntity(entity, reviewRun);
                logger.debug("Updating existing review run entity for runId: {}", reviewRun.runId());
            } else {
                // Create new entity
                entity = reviewRunMapper.toEntity(reviewRun);
                logger.debug("Creating new review run entity for runId: {}", reviewRun.runId());
            }
            
            // Save the entity (cascade will save all associated entities)
            ReviewRunEntity savedEntity = reviewRunRepository.save(entity);
            
            // Convert back to domain object
            ReviewRun savedReviewRun = reviewRunMapper.toDomain(savedEntity);
            
            logger.info("Successfully saved review run {} with entity ID {}", 
                reviewRun.runId(), savedEntity.getId());
            
            return savedReviewRun;
            
        } catch (Exception e) {
            logger.error("Failed to save review run {} atomically", reviewRun.runId(), e);
            throw new ReviewPersistenceException("Atomic save failed for review run " + reviewRun.runId(), e);
        }
    }
    
    /**
     * Find review run by run ID.
     * 
     * @param runId unique run identifier
     * @return review run if found
     */
    public Optional<ReviewRun> findByRunId(String runId) {
        logger.debug("Looking up review run by runId: {}", runId);
        
        return reviewRunRepository.findByRunId(runId)
            .map(reviewRunMapper::toDomain);
    }
    
    /**
     * Find review runs for a specific repository and pull request.
     * 
     * @param repoOwner repository owner
     * @param repoName repository name
     * @param pullNumber pull request number
     * @return list of review runs for the PR
     */
    public List<ReviewRun> findByRepositoryAndPull(String repoOwner, String repoName, String pullNumber) {
        logger.debug("Looking up review runs for {}/{}#{}", repoOwner, repoName, pullNumber);
        
        return reviewRunRepository.findByRepoOwnerAndRepoNameAndPullNumberOrderByCreatedAtDesc(
            repoOwner, repoName, pullNumber)
            .stream()
            .map(reviewRunMapper::toDomain)
            .toList();
    }
    
    /**
     * Find recent review runs for a repository.
     * 
     * @param repoOwner repository owner
     * @param repoName repository name
     * @param limit maximum number of results
     * @return list of recent review runs
     */
    public List<ReviewRun> findRecentByRepository(String repoOwner, String repoName, int limit) {
        logger.debug("Looking up {} recent review runs for {}/{}", limit, repoOwner, repoName);
        
        return reviewRunRepository.findByRepoOwnerAndRepoNameOrderByCreatedAtDesc(
            repoOwner, repoName, Pageable.ofSize(limit).withPage(0))
            .getContent()
            .stream()
            .map(reviewRunMapper::toDomain)
            .toList();
    }
    
    /**
     * Get review statistics for a repository.
     * 
     * @param repoOwner repository owner
     * @param repoName repository name
     * @return repository review statistics
     */
    public RepositoryStats getRepositoryStats(String repoOwner, String repoName) {
        logger.debug("Calculating stats for repository {}/{}", repoOwner, repoName);
        
        List<ReviewRunEntity> runs = reviewRunRepository.findByRepoOwnerAndRepoNameOrderByCreatedAtDesc(
            repoOwner, repoName, Pageable.ofSize(100).withPage(0))
            .getContent(); // Last 100 runs for stats
        
        if (runs.isEmpty()) {
            return new RepositoryStats(repoOwner, repoName, 0, 0.0, 0.0, null, null);
        }
        
        int totalRuns = runs.size();
        double avgScore = runs.stream()
            .filter(r -> r.getScoreTotal() != null)
            .mapToDouble(r -> r.getScoreTotal().doubleValue())
            .average()
            .orElse(0.0);
        
        double avgFindings = runs.stream()
            .mapToDouble(r -> r.getFindings() != null ? r.getFindings().size() : 0.0)
            .average()
            .orElse(0.0);
        
        Instant lastReviewTime = runs.get(0).getStartTime(); // Most recent
        Instant firstReviewTime = runs.get(runs.size() - 1).getStartTime(); // Oldest in this set
        
        return new RepositoryStats(repoOwner, repoName, totalRuns, avgScore, avgFindings, 
            lastReviewTime, firstReviewTime);
    }
    
    /**
     * Delete old review runs (for cleanup).
     * 
     * @param olderThan delete runs older than this timestamp
     * @return number of deleted runs
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public int deleteOldRuns(Instant olderThan) {
        logger.info("Deleting review runs older than {}", olderThan);
        
        long deletedCount = reviewRunRepository.deleteByCreatedAtBefore(olderThan);
        
        logger.info("Deleted {} old review runs", deletedCount);
        return (int) deletedCount;
    }
    
    /**
     * Check if a review is already running for the given PR.
     * 
     * @param repoOwner repository owner
     * @param repoName repository name
     * @param pullNumber pull request number
     * @return true if a review is currently running
     */
    public boolean isReviewRunning(String repoOwner, String repoName, String pullNumber) {
        // A review is considered running if it has status RUNNING
        List<ReviewRunEntity> recentReviews = reviewRunRepository
            .findByRepoOwnerAndRepoNameAndPullNumberOrderByCreatedAtDesc(repoOwner, repoName, pullNumber);
        
        boolean isRunning = recentReviews.stream()
            .anyMatch(review -> review.getStatus() == ReviewRunEntity.ReviewRunStatus.IN_PROGRESS);
        
        if (isRunning) {
            long runningCount = recentReviews.stream()
                .filter(review -> review.getStatus() == ReviewRunEntity.ReviewRunStatus.IN_PROGRESS)
                .count();
            logger.debug("Found {} running reviews for {}/{}#{}", 
                runningCount, repoOwner, repoName, pullNumber);
        }
        
        return isRunning;
    }
    
    /**
     * Mark a running review as failed (cleanup operation).
     * 
     * @param runId run ID to mark as failed
     * @param error error message
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markReviewAsFailed(String runId, String error) {
        Optional<ReviewRunEntity> entityOpt = reviewRunRepository.findByRunId(runId);
        
        if (entityOpt.isPresent()) {
            ReviewRunEntity entity = entityOpt.get();
            entity.setEndTime(Instant.now());
            entity.setStatus(ReviewRunEntity.ReviewRunStatus.FAILED);
            // Could add error field to entity if needed
            
            reviewRunRepository.save(entity);
            logger.info("Marked review run {} as failed: {}", runId, error);
        } else {
            logger.warn("Could not find review run {} to mark as failed", runId);
        }
    }
    
    /**
     * Create an error review run for failed executions.
     */
    private ReviewRun createErrorReviewRun(RepoRef repository, PullRef pullRequest, Throwable error) {
        String errorRunId = "error-" + System.currentTimeMillis();
        Instant now = Instant.now();
        
        return new ReviewRun(
            errorRunId,
            repository,
            pullRequest,
            now,
            List.of(), // No provider keys
            new ReviewRun.Stats(0, 0, 0, 0L, null),
            List.of(), // No findings
            null, // No scores
            null // No artifacts
        );
    }
    
    /**
     * Repository statistics.
     */
    public record RepositoryStats(
        String owner,
        String name,
        int totalRuns,
        double averageScore,
        double averageFindings,
        Instant lastReviewTime,
        Instant firstReviewTime
    ) {
        /**
         * Get review frequency (reviews per day).
         */
        public double getReviewFrequency() {
            if (firstReviewTime == null || lastReviewTime == null || totalRuns <= 1) {
                return 0.0;
            }
            
            long daysBetween = java.time.Duration.between(firstReviewTime, lastReviewTime).toDays();
            return daysBetween > 0 ? (double) totalRuns / daysBetween : totalRuns;
        }
        
        /**
         * Get quality trend indicator.
         */
        public String getQualityTrend() {
            // This would require historical analysis - placeholder for now
            if (averageScore >= 90) return "EXCELLENT";
            if (averageScore >= 80) return "GOOD";
            if (averageScore >= 70) return "FAIR";
            return "NEEDS_IMPROVEMENT";
        }
    }
    
    /**
     * Review persistence exception.
     */
    public static class ReviewPersistenceException extends RuntimeException {
        public ReviewPersistenceException(String message) {
            super(message);
        }
        
        public ReviewPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
