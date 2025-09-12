package com.ai.reviewer.shared.model;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;

import java.util.List;

/**
 * Represents a code review finding or issue.
 * 
 * <p>A finding is an individual issue, suggestion, or observation
 * discovered during the automated code review process. Each finding
 * is associated with a specific location in the code and includes
 * detailed information about the issue and potential solutions.
 *
 * @param id Unique finding identifier across all review runs
 * @param file File path where the finding was detected (relative to repository root)
 * @param startLine Starting line number of the finding (1-based)
 * @param endLine Ending line number of the finding (1-based, inclusive)
 * @param severity Severity level of the finding
 * @param dimension Quality dimension this finding relates to
 * @param title Brief, descriptive title of the finding
 * @param evidence Code snippet or evidence that triggered this finding
 * @param suggestion Detailed suggestion for improvement
 * @param patch Optional suggested code patch in unified diff format
 * @param sources List of analysis sources/tools that identified this finding
 * @param confidence Confidence score of this finding (0.0 to 1.0, where 1.0 is highest confidence)
 */
public record Finding(
    String id,
    String file,
    int startLine,
    int endLine,
    Severity severity,
    Dimension dimension,
    String title,
    String evidence,
    String suggestion,
    String patch,
    List<String> sources,
    double confidence
) {
    /**
     * Get the finding message.
     * @return finding title/message
     */
    public String message() {
        return title;
    }
    
    /**
     * Get the line number.
     * @return start line number
     */
    public Integer line() {
        return startLine;
    }
}
