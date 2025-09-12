package com.ai.reviewer.shared.model;

import com.ai.reviewer.shared.enums.Dimension;

import java.util.Map;

/**
 * Code review scoring results across multiple quality dimensions.
 * 
 * <p>Contains the calculated scores for a review run, including both
 * individual dimensional scores and the overall weighted total score.
 * The scoring system evaluates code quality across different aspects
 * and provides a comprehensive quality assessment.
 *
 * @param totalScore Overall weighted score (0.0 to 100.0) calculated from dimensional scores
 * @param dimensions Individual score for each quality dimension (0.0 to 100.0 per dimension)
 * @param weights Weight configuration used for calculating the total score (sum should equal 1.0)
 */
public record Scores(
    double totalScore,
    Map<Dimension, Double> dimensions,
    Map<Dimension, Double> weights
) {}
