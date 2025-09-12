package com.ai.reviewer.shared.enums;

/**
 * File change status in a diff.
 * 
 * <p>Represents the type of change that occurred to a file in a code diff.
 * This enumeration covers all common file operations tracked by version
 * control systems during code reviews.
 */
public enum FileStatus {
    /** File was newly created */
    ADDED,
    
    /** Existing file was modified */
    MODIFIED,
    
    /** File was deleted */
    DELETED,
    
    /** File was moved or renamed */
    RENAMED
}
