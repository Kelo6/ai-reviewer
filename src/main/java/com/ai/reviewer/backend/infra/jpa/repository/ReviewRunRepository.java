package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.ReviewRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for ReviewRunEntity.
 * 
 * <p>Provides database access methods for review runs with custom
 * query methods for common use cases.
 */
@Repository
public interface ReviewRunRepository extends JpaRepository<ReviewRunEntity, String> {

    /**
     * Find review run by run ID.
     *
     * @param runId the unique run identifier
     * @return optional review run entity
     */
    Optional<ReviewRunEntity> findByRunId(String runId);

    /**
     * Find review runs by repository owner and name.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param pageable pagination information
     * @return page of review runs
     */
    Page<ReviewRunEntity> findByRepoOwnerAndRepoNameOrderByCreatedAtDesc(
            String repoOwner, String repoName, Pageable pageable);

    /**
     * Find review runs by repository and pull request number.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param pullNumber the pull request number
     * @return list of review runs for the specific pull request
     */
    List<ReviewRunEntity> findByRepoOwnerAndRepoNameAndPullNumberOrderByCreatedAtDesc(
            String repoOwner, String repoName, String pullNumber);

    /**
     * Find the latest review run for a specific pull request.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param pullNumber the pull request number
     * @return optional latest review run
     */
    Optional<ReviewRunEntity> findFirstByRepoOwnerAndRepoNameAndPullNumberOrderByCreatedAtDesc(
            String repoOwner, String repoName, String pullNumber);

    /**
     * Find review runs by status.
     *
     * @param status the review run status
     * @param pageable pagination information
     * @return page of review runs with the specified status
     */
    Page<ReviewRunEntity> findByStatusOrderByCreatedAtDesc(
            ReviewRunEntity.ReviewRunStatus status, Pageable pageable);

    /**
     * Find review runs by provider and date range.
     *
     * @param repoProvider the SCM provider
     * @param startDate start of date range
     * @param endDate end of date range
     * @param pageable pagination information
     * @return page of review runs
     */
    Page<ReviewRunEntity> findByRepoProviderAndCreatedAtBetweenOrderByCreatedAtDesc(
            String repoProvider, Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Find review runs created after a specific timestamp.
     *
     * @param timestamp the timestamp threshold
     * @return list of review runs created after the timestamp
     */
    List<ReviewRunEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant timestamp);

    /**
     * Count review runs for a specific repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @return count of review runs
     */
    long countByRepoOwnerAndRepoName(String repoOwner, String repoName);

    /**
     * Find review runs with their findings and scores (using JOIN FETCH for performance).
     * Note: Split into separate queries to avoid MultipleBagFetchException
     *
     * @param runId the run ID
     * @return optional review run with loaded relationships
     */
    @Query("SELECT r FROM ReviewRunEntity r " +
           "LEFT JOIN FETCH r.artifact " +
           "WHERE r.runId = :runId")
    Optional<ReviewRunEntity> findByRunIdWithDetails(@Param("runId") String runId);

    /**
     * Find review runs for a repository with summary information.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param pageable pagination information
     * @return page of review runs
     */
    @Query("SELECT r FROM ReviewRunEntity r " +
           "WHERE r.repoOwner = :repoOwner AND r.repoName = :repoName " +
           "ORDER BY r.createdAt DESC")
    Page<ReviewRunEntity> findRepositoryReviewRuns(
            @Param("repoOwner") String repoOwner, 
            @Param("repoName") String repoName, 
            Pageable pageable);

    /**
     * Delete old review runs older than the specified timestamp.
     *
     * @param cutoffDate the cutoff date for deletion
     * @return number of deleted records
     */
    long deleteByCreatedAtBefore(Instant cutoffDate);

    /**
     * Check if a review run exists for a specific pull request.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param pullNumber the pull request number
     * @return true if at least one review run exists
     */
    boolean existsByRepoOwnerAndRepoNameAndPullNumber(
            String repoOwner, String repoName, String pullNumber);
}
