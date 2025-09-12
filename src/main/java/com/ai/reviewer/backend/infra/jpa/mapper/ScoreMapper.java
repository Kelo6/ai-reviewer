package com.ai.reviewer.backend.infra.jpa.mapper;

import com.ai.reviewer.backend.infra.jpa.entity.ScoreEntity;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.model.Scores;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper for converting between ScoreEntity and Scores record.
 * 
 * <p>Handles the conversion logic between JPA entities and domain records,
 * including aggregation of dimensional scores into the Scores record format.
 */
@Component
public class ScoreMapper {

    /**
     * Convert ScoreEntity to individual dimension score.
     *
     * @param entity the entity to convert
     * @return dimension-score pair
     */
    public Map.Entry<Dimension, Double> toScoreEntry(ScoreEntity entity) {
        if (entity == null) {
            return null;
        }

        return Map.entry(
                entity.getDimension(),
                entity.getScore().doubleValue()
        );
    }

    /**
     * Convert list of ScoreEntity to Scores record.
     *
     * @param entities the score entities to convert
     * @return aggregated scores record
     */
    public Scores toScoresRecord(List<ScoreEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new Scores(0.0, Map.of(), Map.of());
        }

        Map<Dimension, Double> dimensions = new HashMap<>();
        Map<Dimension, Double> weights = new HashMap<>();
        double totalWeightedScore = 0.0;

        for (ScoreEntity entity : entities) {
            Dimension dimension = entity.getDimension();
            double score = entity.getScore().doubleValue();
            double weight = entity.getWeight().doubleValue();

            dimensions.put(dimension, score);
            weights.put(dimension, weight);
            totalWeightedScore += score * weight;
        }

        return new Scores(totalWeightedScore, dimensions, weights);
    }

    /**
     * Convert Scores record to list of ScoreEntity.
     *
     * @param scores the scores record to convert
     * @return list of score entities
     */
    public List<ScoreEntity> toEntities(Scores scores) {
        if (scores == null || scores.dimensions() == null) {
            return List.of();
        }

        return scores.dimensions().entrySet().stream()
                .map(entry -> {
                    ScoreEntity entity = new ScoreEntity();
                    entity.setDimension(entry.getKey());
                    entity.setScore(BigDecimal.valueOf(entry.getValue()));
                    
                    // Get weight for this dimension, defaulting to 0.0 if not found
                    Double weight = scores.weights().getOrDefault(entry.getKey(), 0.0);
                    entity.setWeight(BigDecimal.valueOf(weight));
                    
                    return entity;
                })
                .collect(Collectors.toList());
    }

    /**
     * Convert individual dimension score to ScoreEntity.
     *
     * @param dimension the quality dimension
     * @param score the score value
     * @param weight the weight value
     * @return score entity
     */
    public ScoreEntity toEntity(Dimension dimension, double score, double weight) {
        ScoreEntity entity = new ScoreEntity();
        entity.setDimension(dimension);
        entity.setScore(BigDecimal.valueOf(score));
        entity.setWeight(BigDecimal.valueOf(weight));
        return entity;
    }

    /**
     * Update existing score entities with new scores.
     *
     * @param entities the existing entities to update
     * @param scores the new scores
     */
    public void updateEntities(List<ScoreEntity> entities, Scores scores) {
        if (entities == null || scores == null) {
            return;
        }

        Map<Dimension, ScoreEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(ScoreEntity::getDimension, entity -> entity));

        scores.dimensions().forEach((dimension, score) -> {
            ScoreEntity entity = entityMap.get(dimension);
            if (entity != null) {
                entity.setScore(BigDecimal.valueOf(score));
                Double weight = scores.weights().get(dimension);
                if (weight != null) {
                    entity.setWeight(BigDecimal.valueOf(weight));
                }
            }
        });
    }

    /**
     * Create a score summary from score entities.
     *
     * @param entities the score entities
     * @return score summary
     */
    public ScoresSummary toSummaryRecord(List<ScoreEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ScoresSummary(0.0, Map.of());
        }

        Map<Dimension, Double> dimensionScores = entities.stream()
                .collect(Collectors.toMap(
                        ScoreEntity::getDimension,
                        entity -> entity.getScore().doubleValue()
                ));

        double totalScore = entities.stream()
                .mapToDouble(entity -> entity.getScore().doubleValue() * entity.getWeight().doubleValue())
                .sum();

        return new ScoresSummary(totalScore, dimensionScores);
    }

    /**
     * Calculate weighted total score from score entities.
     *
     * @param entities the score entities
     * @return weighted total score
     */
    public double calculateTotalScore(List<ScoreEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0.0;
        }

        return entities.stream()
                .mapToDouble(entity -> entity.getScore().doubleValue() * entity.getWeight().doubleValue())
                .sum();
    }

    /**
     * Validate that weights sum to 1.0 (or close to it).
     *
     * @param entities the score entities to validate
     * @return true if weights are valid
     */
    public boolean validateWeights(List<ScoreEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }

        double totalWeight = entities.stream()
                .mapToDouble(entity -> entity.getWeight().doubleValue())
                .sum();

        // Allow small floating point errors
        return Math.abs(totalWeight - 1.0) < 0.001;
    }

    /**
     * Get default weights for all dimensions.
     *
     * @return map of dimension to default weight
     */
    public Map<Dimension, Double> getDefaultWeights() {
        Map<Dimension, Double> weights = new HashMap<>();
        weights.put(Dimension.SECURITY, 0.30);
        weights.put(Dimension.QUALITY, 0.25);
        weights.put(Dimension.MAINTAINABILITY, 0.20);
        weights.put(Dimension.PERFORMANCE, 0.15);
        weights.put(Dimension.TEST_COVERAGE, 0.10);
        return weights;
    }

    /**
     * Simple summary record for score information.
     */
    public record ScoresSummary(
            double totalScore,
            Map<Dimension, Double> dimensionScores
    ) {}
}
