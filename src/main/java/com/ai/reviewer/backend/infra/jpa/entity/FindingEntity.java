package com.ai.reviewer.backend.infra.jpa.entity;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity for finding table.
 * 
 * <p>Represents an individual code review finding or issue discovered
 * during the automated analysis process.
 */
@Entity
@Table(name = "finding", indexes = {
    @Index(name = "idx_finding_run_file_severity", 
           columnList = "run_id, file, severity"),
    @Index(name = "idx_finding_dimension_severity", 
           columnList = "dimension, severity, confidence"),
    @Index(name = "idx_finding_confidence", 
           columnList = "confidence")
})
public class FindingEntity {

    @Id
    @Column(name = "id", length = 255)
    @NotBlank
    @Size(max = 255)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, foreignKey = @ForeignKey(name = "fk_finding_run_id"))
    @NotNull
    private ReviewRunEntity reviewRun;

    // File location
    @Column(name = "file", length = 1000, nullable = false)
    @NotBlank
    @Size(max = 1000)
    private String file;

    @Column(name = "start_line", nullable = false)
    @Positive
    private Integer startLine;

    @Column(name = "end_line", nullable = false)
    @Positive
    private Integer endLine;

    // Classification
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @NotNull
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 30)
    @NotNull
    private Dimension dimension;

    // Content
    @Column(name = "title", length = 500, nullable = false)
    @NotBlank
    @Size(max = 500)
    private String title;

    @Column(name = "evidence", columnDefinition = "TEXT", nullable = false)
    @NotBlank
    private String evidence;

    @Column(name = "suggestion", columnDefinition = "TEXT", nullable = false)
    @NotBlank
    private String suggestion;

    @Column(name = "patch", columnDefinition = "TEXT")
    private String patch;

    // Metadata
    @Column(name = "sources", columnDefinition = "JSON", nullable = false)
    @NotBlank
    private String sourcesJson;

    @Column(name = "confidence", precision = 4, scale = 3, nullable = false)
    @NotNull
    @DecimalMin(value = "0.000", inclusive = true)
    @DecimalMax(value = "1.000", inclusive = true)
    private BigDecimal confidence;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    // Constructors
    public FindingEntity() {}

    public FindingEntity(String id) {
        this.id = id;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ReviewRunEntity getReviewRun() { return reviewRun; }
    public void setReviewRun(ReviewRunEntity reviewRun) { this.reviewRun = reviewRun; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }

    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Dimension getDimension() { return dimension; }
    public void setDimension(Dimension dimension) { this.dimension = dimension; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }

    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
