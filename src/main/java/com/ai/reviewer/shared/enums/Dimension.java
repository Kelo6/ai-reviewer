package com.ai.reviewer.shared.enums;

/**
 * Code quality dimensions for scoring and categorization.
 * 
 * <p>Represents different aspects of code quality that are evaluated
 * during the review process. Each dimension focuses on a specific
 * area of software quality and receives a separate score.
 */
public enum Dimension {
    /** Security-related issues (vulnerabilities, data exposure, etc.) */
    SECURITY,
    
    /** General code quality (best practices, code style, design patterns) */
    QUALITY,
    
    /** Code maintainability (readability, complexity, documentation) */
    MAINTAINABILITY,
    
    /** Performance-related concerns (efficiency, resource usage) */
    PERFORMANCE,
    
    /** Test coverage and testing quality */
    TEST_COVERAGE
}
