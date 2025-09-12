package com.ai.reviewer.backend.domain.adapter.scm;

/**
 * Line range specification for file content retrieval.
 * 
 * <p>Used to specify a subset of lines when retrieving file content,
 * which can improve performance when only specific lines are needed
 * for analysis or context.
 *
 * @param startLine starting line number (1-based, inclusive)
 * @param endLine ending line number (1-based, inclusive)
 */
public record Range(
    int startLine,
    int endLine
) {
    
    /**
     * Create a range with validation.
     *
     * @param startLine starting line (must be positive)
     * @param endLine ending line (must be >= startLine)
     */
    public Range {
        if (startLine < 1) {
            throw new IllegalArgumentException("Start line must be positive, got: " + startLine);
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException(
                String.format("End line (%d) must be >= start line (%d)", endLine, startLine));
        }
    }
    
    /**
     * Create a single-line range.
     *
     * @param line line number
     * @return range spanning only the specified line
     */
    public static Range singleLine(int line) {
        return new Range(line, line);
    }
    
    /**
     * Create a range from start to end line.
     *
     * @param startLine starting line number
     * @param endLine ending line number
     * @return line range
     */
    public static Range of(int startLine, int endLine) {
        return new Range(startLine, endLine);
    }
    
    /**
     * Create a range starting from a line and spanning a number of lines.
     *
     * @param startLine starting line number
     * @param lineCount number of lines to include
     * @return line range
     */
    public static Range fromCount(int startLine, int lineCount) {
        if (lineCount < 1) {
            throw new IllegalArgumentException("Line count must be positive, got: " + lineCount);
        }
        return new Range(startLine, startLine + lineCount - 1);
    }
    
    /**
     * Create a range with context around a specific line.
     *
     * @param centerLine center line number
     * @param contextLines number of lines of context before and after
     * @return range with context around the center line
     */
    public static Range withContext(int centerLine, int contextLines) {
        int start = Math.max(1, centerLine - contextLines);
        int end = centerLine + contextLines;
        return new Range(start, end);
    }
    
    /**
     * Check if this range contains only a single line.
     *
     * @return true if start and end line are the same
     */
    public boolean isSingleLine() {
        return startLine == endLine;
    }
    
    /**
     * Get the number of lines in this range.
     *
     * @return line count
     */
    public int getLineCount() {
        return endLine - startLine + 1;
    }
    
    /**
     * Check if this range contains the specified line.
     *
     * @param line line number to check
     * @return true if the line is within this range
     */
    public boolean contains(int line) {
        return line >= startLine && line <= endLine;
    }
    
    /**
     * Check if this range overlaps with another range.
     *
     * @param other other range to check
     * @return true if ranges overlap
     */
    public boolean overlaps(Range other) {
        return startLine <= other.endLine && endLine >= other.startLine;
    }
    
    /**
     * Get the intersection of this range with another range.
     *
     * @param other other range
     * @return intersection range, or null if no overlap
     */
    public Range intersection(Range other) {
        if (!overlaps(other)) {
            return null;
        }
        
        int newStart = Math.max(startLine, other.startLine);
        int newEnd = Math.min(endLine, other.endLine);
        return new Range(newStart, newEnd);
    }
    
    /**
     * Expand this range by the specified number of lines in both directions.
     *
     * @param lines number of lines to expand (minimum start line is 1)
     * @return expanded range
     */
    public Range expand(int lines) {
        int newStart = Math.max(1, startLine - lines);
        int newEnd = endLine + lines;
        return new Range(newStart, newEnd);
    }
    
    /**
     * Get a human-readable string representation.
     *
     * @return range description
     */
    @Override
    public String toString() {
        if (isSingleLine()) {
            return "line " + startLine;
        } else {
            return "lines " + startLine + "-" + endLine;
        }
    }
}
