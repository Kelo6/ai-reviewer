package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.FindingEntity;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data JPA Repository for FindingEntity.
 * 
 * <p>Provides database access methods for code review findings
 * with specialized query methods for analysis and reporting.
 */
@Repository
public interface FindingRepository extends JpaRepository<FindingEntity, String> {

    /**
     * Find all findings for a specific review run.
     *
     * @param runId the review run ID
     * @return list of findings
     */
    List<FindingEntity> findByReviewRunRunIdOrderBySeverityDescConfidenceDesc(String runId);

    /**
     * Find findings by review run and severity.
     *
     * @param runId the review run ID
     * @param severity the finding severity
     * @return list of findings with the specified severity
     */
    List<FindingEntity> findByReviewRunRunIdAndSeverityOrderByConfidenceDesc(
            String runId, Severity severity);

    /**
     * Find findings by review run and dimension.
     *
     * @param runId the review run ID
     * @param dimension the quality dimension
     * @return list of findings in the specified dimension
     */
    List<FindingEntity> findByReviewRunRunIdAndDimensionOrderBySeverityDescConfidenceDesc(
            String runId, Dimension dimension);

    /**
     * Find findings by file path within a review run.
     *
     * @param runId the review run ID
     * @param file the file path
     * @return list of findings for the specified file
     */
    List<FindingEntity> findByReviewRunRunIdAndFileOrderByStartLine(
            String runId, String file);

    /**
     * Find findings with confidence above a threshold.
     *
     * @param runId the review run ID
     * @param minConfidence minimum confidence threshold
     * @return list of high-confidence findings
     */
    List<FindingEntity> findByReviewRunRunIdAndConfidenceGreaterThanEqualOrderBySeverityDescConfidenceDesc(
            String runId, BigDecimal minConfidence);

    /**
     * Count findings by severity for a review run.
     *
     * @param runId the review run ID
     * @param severity the severity level
     * @return count of findings with the specified severity
     */
    long countByReviewRunRunIdAndSeverity(String runId, Severity severity);

    /**
     * Count findings by dimension for a review run.
     *
     * @param runId the review run ID
     * @param dimension the quality dimension
     * @return count of findings in the specified dimension
     */
    long countByReviewRunRunIdAndDimension(String runId, Dimension dimension);

    /**
     * Get finding statistics for a review run.
     *
     * @param runId the review run ID
     * @return list of finding statistics grouped by severity
     */
    @Query("SELECT f.severity, COUNT(f), AVG(f.confidence) " +
           "FROM FindingEntity f " +
           "WHERE f.reviewRun.runId = :runId " +
           "GROUP BY f.severity " +
           "ORDER BY f.severity DESC")
    List<Object[]> findingStatisticsByRunId(@Param("runId") String runId);

    /**
     * Get finding statistics by dimension for a review run.
     *
     * @param runId the review run ID
     * @return list of finding statistics grouped by dimension
     */
    @Query("SELECT f.dimension, f.severity, COUNT(f), AVG(f.confidence) " +
           "FROM FindingEntity f " +
           "WHERE f.reviewRun.runId = :runId " +
           "GROUP BY f.dimension, f.severity " +
           "ORDER BY f.dimension, f.severity DESC")
    List<Object[]> findingStatisticsByDimensionAndRunId(@Param("runId") String runId);

    /**
     * Find findings by multiple files within a review run.
     *
     * @param runId the review run ID
     * @param files list of file paths
     * @return list of findings for the specified files
     */
    List<FindingEntity> findByReviewRunRunIdAndFileInOrderByFileAscStartLineAsc(
            String runId, List<String> files);

    /**
     * Find critical findings for a review run.
     *
     * @param runId the review run ID
     * @return list of critical findings
     */
    @Query("SELECT f FROM FindingEntity f WHERE f.reviewRun.runId = :runId AND f.severity = 'CRITICAL' ORDER BY f.confidence DESC")
    List<FindingEntity> findCriticalFindingsByRunId(@Param("runId") String runId);

    /**
     * Find findings with patches available.
     *
     * @param runId the review run ID
     * @return list of findings that include suggested patches
     */
    List<FindingEntity> findByReviewRunRunIdAndPatchIsNotNullOrderBySeverityDescConfidenceDesc(
            String runId);

    /**
     * Get average confidence by dimension for a review run.
     *
     * @param runId the review run ID
     * @return list of average confidence scores grouped by dimension
     */
    @Query("SELECT f.dimension, AVG(f.confidence) " +
           "FROM FindingEntity f " +
           "WHERE f.reviewRun.runId = :runId " +
           "GROUP BY f.dimension " +
           "ORDER BY f.dimension")
    List<Object[]> averageConfidenceByDimension(@Param("runId") String runId);

    /**
     * Delete findings for a specific review run.
     *
     * @param runId the review run ID
     * @return number of deleted findings
     */
    long deleteByReviewRunRunId(String runId);

    /**
     * Find findings with pagination for a review run.
     *
     * @param runId the review run ID
     * @param pageable pagination information
     * @return page of findings
     */
    Page<FindingEntity> findByReviewRunRunIdOrderBySeverityDescConfidenceDesc(
            String runId, Pageable pageable);
}
