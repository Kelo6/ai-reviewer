package com.ai.reviewer.shared.enums;

/**
 * Finding severity levels for code review issues.
 * 
 * <p>Represents the severity or impact level of a code review finding.
 * This classification helps prioritize issues and guide developer attention
 * to the most critical problems first.
 */
public enum Severity {
    /** Informational finding - no action required but good to know */
    INFO,
    
    /** Minor issue - should be addressed but not blocking */
    MINOR,
    
    /** Major issue - should be fixed before merge */
    MAJOR,
    
    /** Critical issue - must be fixed immediately */
    CRITICAL
}
