package com.ai.reviewer.backend.infra.jpa.repository;

import com.ai.reviewer.backend.infra.jpa.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for ArtifactEntity.
 * 
 * <p>Provides database access methods for review artifacts
 * with methods for file management and cleanup operations.
 */
@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {

    /**
     * Find artifact by review run ID.
     *
     * @param runId the review run ID
     * @return optional artifact entity
     */
    Optional<ArtifactEntity> findByReviewRunRunId(String runId);

    /**
     * Find artifacts with SARIF reports.
     *
     * @return list of artifacts that have SARIF reports
     */
    List<ArtifactEntity> findBySarifPathIsNotNull();

    /**
     * Find artifacts with PDF reports.
     *
     * @return list of artifacts that have PDF reports
     */
    List<ArtifactEntity> findByReportPdfPathIsNotNull();

    /**
     * Find artifacts with HTML reports.
     *
     * @return list of artifacts that have HTML reports
     */
    List<ArtifactEntity> findByReportHtmlPathIsNotNull();

    /**
     * Find artifacts with Markdown reports.
     *
     * @return list of artifacts that have Markdown reports
     */
    List<ArtifactEntity> findByReportMdPathIsNotNull();

    /**
     * Find artifacts created before a specific timestamp.
     *
     * @param cutoffDate the cutoff timestamp
     * @return list of old artifacts
     */
    List<ArtifactEntity> findByCreatedAtBefore(Instant cutoffDate);

    /**
     * Calculate total file size of all artifacts.
     *
     * @return total file size in bytes
     */
    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) FROM ArtifactEntity a")
    Long getTotalFileSizeBytes();

    /**
     * Calculate total file size for a specific repository.
     *
     * @param repoOwner the repository owner
     * @param repoName the repository name
     * @return total file size for the repository
     */
    @Query("SELECT COALESCE(SUM(a.fileSizeBytes), 0) " +
           "FROM ArtifactEntity a " +
           "WHERE a.reviewRun.repoOwner = :repoOwner " +
           "AND a.reviewRun.repoName = :repoName")
    Long getTotalFileSizeByRepository(
            @Param("repoOwner") String repoOwner,
            @Param("repoName") String repoName);

    /**
     * Find artifacts with file size above a threshold.
     *
     * @param minSizeBytes minimum file size in bytes
     * @return list of large artifacts
     */
    List<ArtifactEntity> findByFileSizeBytesGreaterThanOrderByFileSizeBytesDesc(Long minSizeBytes);

    /**
     * Count artifacts with complete report sets (all report types available).
     *
     * @return count of complete artifact sets
     */
    @Query("SELECT COUNT(a) " +
           "FROM ArtifactEntity a " +
           "WHERE a.sarifPath IS NOT NULL " +
           "AND a.reportMdPath IS NOT NULL " +
           "AND a.reportHtmlPath IS NOT NULL " +
           "AND a.reportPdfPath IS NOT NULL")
    Long countCompleteArtifactSets();

    /**
     * Find artifacts missing specific report types.
     *
     * @param reportType the report type to check (sarif, md, html, pdf)
     * @return list of artifacts missing the specified report type
     */
    @Query("SELECT a FROM ArtifactEntity a " +
           "WHERE (:reportType = 'sarif' AND a.sarifPath IS NULL) " +
           "OR (:reportType = 'md' AND a.reportMdPath IS NULL) " +
           "OR (:reportType = 'html' AND a.reportHtmlPath IS NULL) " +
           "OR (:reportType = 'pdf' AND a.reportPdfPath IS NULL)")
    List<ArtifactEntity> findArtifactsMissingReportType(@Param("reportType") String reportType);

    /**
     * Get file size statistics.
     *
     * @return array containing min, max, average, and total file sizes
     */
    @Query("SELECT MIN(a.fileSizeBytes), MAX(a.fileSizeBytes), AVG(a.fileSizeBytes), SUM(a.fileSizeBytes) " +
           "FROM ArtifactEntity a " +
           "WHERE a.fileSizeBytes IS NOT NULL AND a.fileSizeBytes > 0")
    Optional<Object[]> getFileSizeStatistics();

    /**
     * Delete artifacts by review run ID.
     *
     * @param runId the review run ID
     * @return number of deleted artifacts
     */
    Long deleteByReviewRunRunId(String runId);

    /**
     * Delete old artifacts before a cutoff date.
     *
     * @param cutoffDate the cutoff timestamp
     * @return number of deleted artifacts
     */
    Long deleteByCreatedAtBefore(Instant cutoffDate);

    /**
     * Check if an artifact exists for a review run.
     *
     * @param runId the review run ID
     * @return true if artifact exists
     */
    boolean existsByReviewRunRunId(String runId);

    /**
     * Find artifacts by file path patterns (for cleanup operations).
     *
     * @param pathPattern the path pattern to match
     * @return list of matching artifacts
     */
    @Query("SELECT a FROM ArtifactEntity a " +
           "WHERE a.sarifPath LIKE :pattern " +
           "OR a.reportMdPath LIKE :pattern " +
           "OR a.reportHtmlPath LIKE :pattern " +
           "OR a.reportPdfPath LIKE :pattern " +
           "OR a.rawDataPath LIKE :pattern")
    List<ArtifactEntity> findByPathPattern(@Param("pattern") String pathPattern);
}
