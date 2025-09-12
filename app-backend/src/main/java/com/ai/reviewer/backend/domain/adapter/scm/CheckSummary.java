package com.ai.reviewer.backend.domain.adapter.scm;

/**
 * Summary information for creating/updating SCM check runs.
 * 
 * <p>Represents the overall status of an AI code review that gets
 * posted to the SCM provider as a check run or status check.
 * This appears as a green checkmark, red X, or yellow warning
 * on the pull request page.
 *
 * @param title brief title for the check (e.g., "AI Code Review")
 * @param conclusion check conclusion status (success, failure, neutral, etc.)
 * @param detailsUrl URL to detailed results (optional, can be null)
 */
public record CheckSummary(
    String title,
    String conclusion,
    String detailsUrl
) {
    
    /**
     * Create a successful check summary.
     *
     * @param title check title
     * @param detailsUrl URL to detailed results
     * @return success check summary
     */
    public static CheckSummary success(String title, String detailsUrl) {
        return new CheckSummary(title, Conclusion.SUCCESS, detailsUrl);
    }
    
    /**
     * Create a failed check summary.
     *
     * @param title check title
     * @param detailsUrl URL to detailed results
     * @return failure check summary
     */
    public static CheckSummary failure(String title, String detailsUrl) {
        return new CheckSummary(title, Conclusion.FAILURE, detailsUrl);
    }
    
    /**
     * Create a neutral check summary.
     *
     * @param title check title
     * @param detailsUrl URL to detailed results
     * @return neutral check summary
     */
    public static CheckSummary neutral(String title, String detailsUrl) {
        return new CheckSummary(title, Conclusion.NEUTRAL, detailsUrl);
    }
    
    /**
     * Create a pending check summary.
     *
     * @param title check title
     * @return pending check summary
     */
    public static CheckSummary pending(String title) {
        return new CheckSummary(title, Conclusion.PENDING, null);
    }
    
    /**
     * Create an in-progress check summary.
     *
     * @param title check title
     * @return in-progress check summary
     */
    public static CheckSummary inProgress(String title) {
        return new CheckSummary(title, Conclusion.IN_PROGRESS, null);
    }
    
    /**
     * Check if this represents a successful review.
     *
     * @return true if conclusion is success
     */
    public boolean isSuccess() {
        return Conclusion.SUCCESS.equals(conclusion);
    }
    
    /**
     * Check if this represents a failed review.
     *
     * @return true if conclusion is failure
     */
    public boolean isFailure() {
        return Conclusion.FAILURE.equals(conclusion);
    }
    
    /**
     * Check if this represents a pending or in-progress review.
     *
     * @return true if review is not yet complete
     */
    public boolean isPending() {
        return Conclusion.PENDING.equals(conclusion) || 
               Conclusion.IN_PROGRESS.equals(conclusion);
    }
    
    /**
     * Check if this summary includes a details URL.
     *
     * @return true if details URL is available
     */
    public boolean hasDetailsUrl() {
        return detailsUrl != null && !detailsUrl.trim().isEmpty();
    }
    
    /**
     * Standard conclusion constants that map to common SCM provider status values.
     */
    public static final class Conclusion {
        /** Review completed successfully with no critical issues */
        public static final String SUCCESS = "success";
        
        /** Review found critical issues that must be addressed */
        public static final String FAILURE = "failure";
        
        /** Review completed but found some issues (informational) */
        public static final String NEUTRAL = "neutral";
        
        /** Review is pending/queued but not yet started */
        public static final String PENDING = "pending";
        
        /** Review is currently in progress */
        public static final String IN_PROGRESS = "in_progress";
        
        /** Review was cancelled or skipped */
        public static final String CANCELLED = "cancelled";
        
        /** Review encountered an error and could not complete */
        public static final String ERROR = "error";
        
        private Conclusion() {}
    }
}
