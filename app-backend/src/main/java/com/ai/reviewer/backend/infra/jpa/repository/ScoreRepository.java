package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.ScoreEntity;
import com.ai.reviewer.backend.infra.jpa.entity.ScoreEntityId;
import com.ai.reviewer.shared.enums.Dimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for ScoreEntity.
 * 
 * <p>Provides database access methods for dimensional scores
 * with methods for score calculation and analysis.
 */
@Repository
public interface ScoreRepository extends JpaRepository<ScoreEntity, ScoreEntityId> {

    /**
     * Find all scores for a specific review run.
     *
     * @param runId the review run ID
     * @return list of scores for all dimensions
     */
    List<ScoreEntity> findByReviewRunRunIdOrderByDimension(String runId);

    /**
     * Find score for a specific review run and dimension.
     *
     * @param runId the review run ID
     * @param dimension the quality dimension
     * @return optional score entity
     */
    Optional<ScoreEntity> findByReviewRunRunIdAndDimension(String runId, Dimension dimension);

    /**
     * Find scores by dimension across all review runs.
     *
     * @param dimension the quality dimension
     * @return list of scores for the specified dimension
     */
    List<ScoreEntity> findByDimensionOrderByScoreDesc(Dimension dimension);

    /**
     * Count scores for a review run.
     *
     * @param runId the review run ID
     * @return count of dimensional scores
     */
    long countByReviewRunRunId(String runId);

    /**
     * Calculate weighted total score for a review run.
     *
     * @param runId the review run ID
     * @return weighted total score
     */
    @Query("SELECT SUM(s.score * s.weight) " +
           "FROM ScoreEntity s " +
           "WHERE s.reviewRun.runId = :runId")
    Optional<BigDecimal> calculateWeightedTotalScore(@Param("runId") String runId);

    /**
     * Get score statistics for a specific dimension.
     *
     * @param dimension the quality dimension
     * @return statistics: min, max, average scores
     */
    @Query("SELECT MIN(s.score), MAX(s.score), AVG(s.score), COUNT(s) " +
           "FROM ScoreEntity s " +
           "WHERE s.dimension = :dimension")
    Optional<Object[]> getScoreStatistics(@Param("dimension") Dimension dimension);

    /**
     * Find review runs with scores above a threshold for a dimension.
     *
     * @param dimension the quality dimension
     * @param minScore minimum score threshold
     * @return list of score entities above the threshold
     */
    List<ScoreEntity> findByDimensionAndScoreGreaterThanEqualOrderByScoreDesc(
            Dimension dimension, BigDecimal minScore);

    /**
     * Find review runs with scores below a threshold for a dimension.
     *
     * @param dimension the quality dimension
     * @param maxScore maximum score threshold
     * @return list of score entities below the threshold
     */
    List<ScoreEntity> findByDimensionAndScoreLessThanOrderByScoreAsc(
            Dimension dimension, BigDecimal maxScore);

    /**
     * Get average scores by dimension across all review runs.
     *
     * @return list of average scores grouped by dimension
     */
    @Query("SELECT s.dimension, AVG(s.score), AVG(s.weight) " +
           "FROM ScoreEntity s " +
           "GROUP BY s.dimension " +
           "ORDER BY s.dimension")
    List<Object[]> getAverageScoresByDimension();

    /**
     * Get score trends for a repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @param dimension the quality dimension
     * @return list of scores ordered by creation time
     */
    @Query("SELECT s.score, s.reviewRun.createdAt " +
           "FROM ScoreEntity s " +
           "WHERE s.reviewRun.repoOwner = :repoOwner " +
           "AND s.reviewRun.repoName = :repoName " +
           "AND s.dimension = :dimension " +
           "ORDER BY s.reviewRun.createdAt")
    List<Object[]> getScoreTrends(
            @Param("repoOwner") String repoOwner,
            @Param("repoName") String repoName,
            @Param("dimension") Dimension dimension);

    /**
     * Find the best performing review runs by total weighted score.
     *
     * @param limit maximum number of results
     * @return list of top-scoring review runs
     */
    @Query(value = "SELECT run_id, SUM(score * weight) as total_score " +
                   "FROM score " +
                   "GROUP BY run_id " +
                   "ORDER BY total_score DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopScoringReviewRuns(@Param("limit") int limit);

    /**
     * Delete scores for a specific review run.
     *
     * @param runId the review run ID
     * @return number of deleted scores
     */
    long deleteByReviewRunRunId(String runId);

    /**
     * Check if all expected dimensions have scores for a review run.
     *
     * @param runId the review run ID
     * @param expectedDimensionCount expected number of dimensions
     * @return true if all dimensions are scored
     */
    @Query("SELECT COUNT(s) = :expectedCount " +
           "FROM ScoreEntity s " +
           "WHERE s.reviewRun.runId = :runId")
    boolean hasAllDimensionScores(
            @Param("runId") String runId,
            @Param("expectedCount") long expectedDimensionCount);
}
