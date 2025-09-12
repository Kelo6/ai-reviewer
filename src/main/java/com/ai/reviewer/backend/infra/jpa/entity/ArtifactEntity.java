package com.ai.reviewer.backend.infra.jpa.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA Entity for artifact table.
 * 
 * <p>Represents generated reports and artifacts for a review run,
 * storing file paths and metadata for all output files.
 */
@Entity
@Table(name = "artifact", indexes = {
    @Index(name = "idx_artifact_size", 
           columnList = "file_size_bytes, created_at")
})
public class ArtifactEntity {

    @Id
    @Column(name = "run_id")
    @NotNull
    private String runId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", foreignKey = @ForeignKey(name = "fk_artifact_run_id"), insertable = false, updatable = false)
    @NotNull
    private ReviewRunEntity reviewRun;

    // Report file paths
    @Column(name = "sarif_path", length = 1000)
    @Size(max = 1000)
    private String sarifPath;

    @Column(name = "report_md_path", length = 1000)
    @Size(max = 1000)
    private String reportMdPath;

    @Column(name = "report_html_path", length = 1000)
    @Size(max = 1000)
    private String reportHtmlPath;

    @Column(name = "report_pdf_path", length = 1000)
    @Size(max = 1000)
    private String reportPdfPath;

    // Additional artifacts
    @Column(name = "raw_data_path", length = 1000)
    @Size(max = 1000)
    private String rawDataPath;

    // Metadata
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "file_size_bytes")
    @PositiveOrZero
    private Long fileSizeBytes = 0L;

    // Constructors
    public ArtifactEntity() {}

    public ArtifactEntity(String runId, ReviewRunEntity reviewRun) {
        this.runId = runId;
        this.reviewRun = reviewRun;
    }

    // Getters and setters
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    
    public ReviewRunEntity getReviewRun() { return reviewRun; }
    public void setReviewRun(ReviewRunEntity reviewRun) { this.reviewRun = reviewRun; }

    public String getSarifPath() { return sarifPath; }
    public void setSarifPath(String sarifPath) { this.sarifPath = sarifPath; }

    public String getReportMdPath() { return reportMdPath; }
    public void setReportMdPath(String reportMdPath) { this.reportMdPath = reportMdPath; }

    public String getReportHtmlPath() { return reportHtmlPath; }
    public void setReportHtmlPath(String reportHtmlPath) { this.reportHtmlPath = reportHtmlPath; }

    public String getReportPdfPath() { return reportPdfPath; }
    public void setReportPdfPath(String reportPdfPath) { this.reportPdfPath = reportPdfPath; }

    public String getRawDataPath() { return rawDataPath; }
    public void setRawDataPath(String rawDataPath) { this.rawDataPath = rawDataPath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
}
