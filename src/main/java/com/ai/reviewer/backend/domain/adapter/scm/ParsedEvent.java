package com.ai.reviewer.backend.domain.adapter.scm;

import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;

/**
 * Parsed webhook event from SCM providers.
 * 
 * <p>Represents a normalized webhook event that has been parsed from
 * provider-specific payloads. This provides a unified view of webhook
 * events across different SCM platforms.
 * 
 * <p>Common event types include:
 * <ul>
 *   <li>pull_request.opened - New pull request created</li>
 *   <li>pull_request.synchronize - Pull request updated with new commits</li>
 *   <li>pull_request.closed - Pull request closed/merged</li>
 *   <li>push - Direct push to branch (may trigger review of recent PRs)</li>
 * </ul>
 *
 * @param type normalized event type (e.g., "pull_request.opened", "pull_request.synchronize")
 * @param repo repository where the event occurred
 * @param pull pull request reference (may be null for non-PR events)
 */
public record ParsedEvent(
    String type,
    RepoRef repo,
    PullRef pull
) {
    
    /**
     * Check if this event is related to a pull request.
     *
     * @return true if this event has pull request information
     */
    public boolean isPullRequestEvent() {
        return pull != null && type != null && type.startsWith("pull_request");
    }
    
    /**
     * Check if this event indicates a new pull request was opened.
     *
     * @return true if this is a PR opened event
     */
    public boolean isPullRequestOpened() {
        return "pull_request.opened".equals(type);
    }
    
    /**
     * Check if this event indicates a pull request was updated with new commits.
     *
     * @return true if this is a PR synchronize event
     */
    public boolean isPullRequestSynchronized() {
        return "pull_request.synchronize".equals(type);
    }
    
    /**
     * Check if this event indicates a pull request was closed.
     *
     * @return true if this is a PR closed event
     */
    public boolean isPullRequestClosed() {
        return "pull_request.closed".equals(type);
    }
    
    /**
     * Check if this event should trigger an AI code review.
     * 
     * <p>Typically, reviews are triggered for opened and synchronized events,
     * but not for closed events.
     *
     * @return true if this event should trigger a review
     */
    public boolean shouldTriggerReview() {
        return isPullRequestOpened() || isPullRequestSynchronized();
    }
    
    /**
     * Get a human-readable description of this event.
     *
     * @return event description
     */
    public String getDescription() {
        if (pull != null) {
            return switch (type) {
                case "pull_request.opened" -> 
                    String.format("Pull request #%s opened in %s/%s", 
                        pull.number(), repo.owner(), repo.name());
                case "pull_request.synchronize" -> 
                    String.format("Pull request #%s updated in %s/%s", 
                        pull.number(), repo.owner(), repo.name());
                case "pull_request.closed" -> 
                    String.format("Pull request #%s closed in %s/%s", 
                        pull.number(), repo.owner(), repo.name());
                default -> 
                    String.format("Pull request #%s event '%s' in %s/%s", 
                        pull.number(), type, repo.owner(), repo.name());
            };
        } else {
            return String.format("Event '%s' in %s/%s", type, repo.owner(), repo.name());
        }
    }
    
    /**
     * Common event type constants.
     */
    public static final class EventTypes {
        public static final String PULL_REQUEST_OPENED = "pull_request.opened";
        public static final String PULL_REQUEST_SYNCHRONIZE = "pull_request.synchronize";
        public static final String PULL_REQUEST_CLOSED = "pull_request.closed";
        public static final String PUSH = "push";
        
        private EventTypes() {}
    }
}
