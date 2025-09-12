package com.ai.reviewer.shared.model;

import com.ai.reviewer.shared.enums.FileStatus;

/**
 * Represents a diff hunk for a file change.
 * 
 * <p>A diff hunk contains the actual changes made to a file, including
 * the unified diff format patch and metadata about the change. This is
 * the atomic unit of code changes that will be analyzed during reviews.
 *
 * @param file Current file path relative to the repository root
 * @param status Type of change applied to this file
 * @param patch Unified diff format patch showing the actual changes
 * @param oldPath Original file path (only relevant for renamed files, null otherwise)
 * @param linesAdded Number of lines added in this hunk
 * @param linesDeleted Number of lines deleted in this hunk
 */
public record DiffHunk(
    String file,
    FileStatus status,
    String patch,
    String oldPath,
    int linesAdded,
    int linesDeleted
) {
    /**
     * Get the file path.
     * @return file path
     */
    public String filePath() {
        return file;
    }
}
