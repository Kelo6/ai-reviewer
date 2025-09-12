package com.ai.reviewer.backend.domain.adapter.scm;

/**
 * Inline comment to be posted on a specific line of code in a pull request.
 * 
 * <p>Represents a code review comment that appears directly in the diff view,
 * allowing AI feedback to be shown in context with the relevant code changes.
 * Different SCM providers have varying support for comment positioning and features.
 *
 * @param file file path relative to repository root where the comment should appear
 * @param line line number where the comment should be placed (1-based)
 * @param body comment content (supports Markdown formatting in most providers)
 * @param side which side of the diff to comment on ("LEFT" for old, "RIGHT" for new, null for default)
 * @param startLine starting line for multi-line comments (optional, null for single-line)
 */
public record InlineComment(
    String file,
    Integer line,
    String body,
    String side,
    Integer startLine
) {
    
    /**
     * Create a single-line comment on the new/right side of the diff.
     *
     * @param file file path
     * @param line line number
     * @param body comment body
     * @return inline comment
     */
    public static InlineComment onNewLine(String file, int line, String body) {
        return new InlineComment(file, line, body, Side.RIGHT, null);
    }
    
    /**
     * Create a single-line comment on the old/left side of the diff.
     *
     * @param file file path
     * @param line line number
     * @param body comment body
     * @return inline comment
     */
    public static InlineComment onOldLine(String file, int line, String body) {
        return new InlineComment(file, line, body, Side.LEFT, null);
    }
    
    /**
     * Create a multi-line comment spanning multiple lines on the new side.
     *
     * @param file file path
     * @param startLine starting line number
     * @param endLine ending line number
     * @param body comment body
     * @return multi-line inline comment
     */
    public static InlineComment onNewLines(String file, int startLine, int endLine, String body) {
        return new InlineComment(file, endLine, body, Side.RIGHT, startLine);
    }
    
    /**
     * Create a multi-line comment spanning multiple lines on the old side.
     *
     * @param file file path
     * @param startLine starting line number
     * @param endLine ending line number
     * @param body comment body
     * @return multi-line inline comment
     */
    public static InlineComment onOldLines(String file, int startLine, int endLine, String body) {
        return new InlineComment(file, endLine, body, Side.LEFT, startLine);
    }
    
    /**
     * Create a simple comment without specifying the side (provider default).
     *
     * @param file file path
     * @param line line number
     * @param body comment body
     * @return simple inline comment
     */
    public static InlineComment simple(String file, int line, String body) {
        return new InlineComment(file, line, body, null, null);
    }
    
    /**
     * Check if this is a multi-line comment.
     *
     * @return true if this comment spans multiple lines
     */
    public boolean isMultiLine() {
        return startLine != null && startLine.intValue() != line.intValue();
    }
    
    /**
     * Check if this comment is on the old/left side of the diff.
     *
     * @return true if commenting on old side
     */
    public boolean isOnOldSide() {
        return Side.LEFT.equals(side);
    }
    
    /**
     * Check if this comment is on the new/right side of the diff.
     *
     * @return true if commenting on new side
     */
    public boolean isOnNewSide() {
        return Side.RIGHT.equals(side);
    }
    
    /**
     * Get the effective starting line (handles single-line comments).
     *
     * @return starting line number
     */
    public int getEffectiveStartLine() {
        return startLine != null ? startLine : line;
    }
    
    /**
     * Get the line range for this comment.
     *
     * @return number of lines this comment spans
     */
    public int getLineCount() {
        if (!isMultiLine()) {
            return 1;
        }
        return line - startLine + 1;
    }
    
    /**
     * Create a sanitized version of the comment body (remove null/empty content).
     *
     * @return sanitized comment body
     */
    public String getSanitizedBody() {
        return body != null ? body.trim() : "";
    }
    
    /**
     * Check if this comment has valid content.
     *
     * @return true if body is not null or empty
     */
    public boolean hasValidContent() {
        return body != null && !body.trim().isEmpty();
    }
    
    /**
     * Diff side constants.
     */
    public static final class Side {
        /** Left side of diff (old/original code) */
        public static final String LEFT = "LEFT";
        
        /** Right side of diff (new/modified code) */
        public static final String RIGHT = "RIGHT";
        
        private Side() {}
    }
}
