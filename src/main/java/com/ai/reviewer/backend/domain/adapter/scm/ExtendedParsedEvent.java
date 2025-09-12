package com.ai.reviewer.backend.domain.adapter.scm;

import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;

import java.time.Instant;
import java.util.Map;

/**
 * Extended parsed event with additional fields for new multi-repo adapter.
 */
public record ExtendedParsedEvent(
    EventType eventType,
    RepoRef repo,
    PullRef pull,
    String commitSha,
    Instant timestamp,
    Map<String, Object> metadata
) {
    
    public enum EventType {
        PULL_REQUEST_OPENED,
        PULL_REQUEST_UPDATED,
        PULL_REQUEST_MERGED,
        PULL_REQUEST_CLOSED,
        PUSH,
        UNKNOWN
    }
    
    /**
     * Convert to simple ParsedEvent for backwards compatibility.
     */
    public ParsedEvent toSimpleEvent() {
        String type = switch (eventType) {
            case PULL_REQUEST_OPENED -> "pull_request.opened";
            case PULL_REQUEST_UPDATED -> "pull_request.synchronize";
            case PULL_REQUEST_MERGED -> "pull_request.closed";
            case PULL_REQUEST_CLOSED -> "pull_request.closed";
            case PUSH -> "push";
            case UNKNOWN -> "unknown";
        };
        
        return new ParsedEvent(type, repo, pull);
    }
    
    /**
     * Create from simple ParsedEvent.
     */
    public static ExtendedParsedEvent fromSimpleEvent(ParsedEvent event) {
        EventType type = switch (event.type()) {
            case "pull_request.opened" -> EventType.PULL_REQUEST_OPENED;
            case "pull_request.synchronize", "pull_request.updated" -> EventType.PULL_REQUEST_UPDATED;
            case "pull_request.closed" -> EventType.PULL_REQUEST_CLOSED;
            case "push" -> EventType.PUSH;
            default -> EventType.UNKNOWN;
        };
        
        return new ExtendedParsedEvent(type, event.repo(), event.pull(), null, Instant.now(), Map.of());
    }
}
