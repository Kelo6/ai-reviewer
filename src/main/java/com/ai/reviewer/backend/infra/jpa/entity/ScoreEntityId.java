package com.ai.reviewer.backend.infra.jpa.entity;

import com.ai.reviewer.shared.enums.Dimension;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for ScoreEntity.
 * 
 * <p>Combines ReviewRunEntity and Dimension to form a unique
 * composite key for the score table.
 */
public class ScoreEntityId implements Serializable {

    private String reviewRun; // This should match the type of ReviewRunEntity's ID
    private Dimension dimension;

    // Constructors
    public ScoreEntityId() {}

    public ScoreEntityId(String reviewRun, Dimension dimension) {
        this.reviewRun = reviewRun;
        this.dimension = dimension;
    }

    // Getters and setters
    public String getReviewRun() { return reviewRun; }
    public void setReviewRun(String reviewRun) { this.reviewRun = reviewRun; }

    public Dimension getDimension() { return dimension; }
    public void setDimension(Dimension dimension) { this.dimension = dimension; }

    // equals() and hashCode()
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreEntityId that = (ScoreEntityId) o;
        return Objects.equals(reviewRun, that.reviewRun) && 
               dimension == that.dimension;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewRun, dimension);
    }

    @Override
    public String toString() {
        return "ScoreEntityId{" +
               "reviewRun='" + reviewRun + '\'' +
               ", dimension=" + dimension +
               '}';
    }
}
