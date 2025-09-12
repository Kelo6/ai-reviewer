package com.ai.reviewer.backend.infra.jpa.entity;

import com.ai.reviewer.shared.enums.Dimension;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity for score table.
 * 
 * <p>Represents a dimensional score for a specific review run,
 * storing the calculated quality score and weight for each dimension.
 */
@Entity
@Table(name = "score")
@IdClass(ScoreEntityId.class)
public class ScoreEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, foreignKey = @ForeignKey(name = "fk_score_run_id"))
    @NotNull
    private ReviewRunEntity reviewRun;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 30)
    @NotNull
    private Dimension dimension;

    @Column(name = "score", precision = 5, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @DecimalMax(value = "100.00", inclusive = true)
    private BigDecimal score;

    @Column(name = "weight", precision = 4, scale = 3, nullable = false)
    @NotNull
    @DecimalMin(value = "0.000", inclusive = true)
    @DecimalMax(value = "1.000", inclusive = true)
    private BigDecimal weight;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    // Constructors
    public ScoreEntity() {}

    public ScoreEntity(ReviewRunEntity reviewRun, Dimension dimension) {
        this.reviewRun = reviewRun;
        this.dimension = dimension;
    }

    // Getters and setters
    public ReviewRunEntity getReviewRun() { return reviewRun; }
    public void setReviewRun(ReviewRunEntity reviewRun) { this.reviewRun = reviewRun; }

    public Dimension getDimension() { return dimension; }
    public void setDimension(Dimension dimension) { this.dimension = dimension; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
