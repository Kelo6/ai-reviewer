package com.ai.reviewer.backend.infra.adapter.gitea;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Gitea SCM adapter implementation.
 * 
 * Gitea API is similar to GitHub API, so we can reuse many patterns.
 * Supports self-hosted Gitea instances.
 */
@Component
public class GiteaAdapter implements ScmAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GiteaAdapter.class);
    
    private final GiteaConfig config;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public GiteaAdapter(GiteaConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        
        if (config.isConfigured()) {
            logger.info("Gitea adapter initialized for: {}", config.getApiBase());
        }
    }
    
    @Override
    public String getProvider() {
        return "gitea";
    }
    
    @Override
    public boolean supports(RepoRef repo) {
        if (repo == null || repo.provider() == null) {
            return false;
        }
        
        String provider = repo.provider().toLowerCase().trim();
        
        // Support gitea provider or custom gitea instances
        if ("gitea".equals(provider)) {
            return true;
        }
        
        // Check if repo URL matches configured Gitea instance
        if (config.isConfigured() && repo.url() != null) {
            String baseHost = extractHost(config.getApiBase());
            String repoHost = extractHost(repo.url());
            return Objects.equals(baseHost, repoHost);
        }
        
        return false;
    }
    
    @Override
    public boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody) {
        // Gitea uses similar webhook signature verification as GitHub
        try {
            String signature = headers.get("X-Gitea-Signature");
            if (signature == null) {
                signature = headers.get("X-Hub-Signature-256");
            }
            if (signature == null) {
                signature = headers.get("X-Hub-Signature");
            }
            
            if (signature == null) {
                logger.debug("No signature header found in Gitea webhook");
                return config.getWebhookSecret() == null || config.getWebhookSecret().trim().isEmpty();
            }
            
            return verifyHmacSignature(signature, rawBody, config.getWebhookSecret());
            
        } catch (Exception e) {
            logger.error("Failed to verify Gitea webhook signature", e);
            return false;
        }
    }
    
    @Override
    public ParsedEvent parseEvent(byte[] payload, Map<String, String> headers) {
        try {
            String eventType = headers.get("X-Gitea-Event");
            if (eventType == null) {
                eventType = headers.get("X-GitHub-Event"); // Fallback for GitHub-compatible mode
            }
            
            if (eventType == null) {
                throw new GiteaException("parseEvent", "Missing event type header");
            }
            
            JsonNode json = objectMapper.readTree(payload);
            
            switch (eventType) {
                case "pull_request":
                    return parsePullRequestEvent(json);
                case "push":
                    return parsePushEvent(json);
                default:
                    logger.debug("Unsupported Gitea event type: {}", eventType);
                    return new ParsedEvent("unknown", null, null);
            }
            
        } catch (Exception e) {
            throw new GiteaException("parseEvent", "Failed to parse webhook payload", e);
        }
    }
    
    @Override
    public PullRef getPull(RepoRef repo, String number) {
        // TODO: Implement API call to get pull request details
        throw new GiteaException("getPull", "Not yet implemented");
    }
    
    @Override
    public List<DiffHunk> listDiff(RepoRef repo, PullRef pull) {
        // TODO: Implement API call to get pull request diff
        throw new GiteaException("listDiff", "Not yet implemented");
    }
    
    @Override
    public String getFileBlob(RepoRef repo, String sha, String path, Range range) {
        // TODO: Implement API call to get file content
        throw new GiteaException("getFileBlob", "Not yet implemented");
    }
    
    @Override
    public void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary) {
        // TODO: Implement API call to update commit status
        logger.debug("Gitea check update not yet implemented for PR {}", pull.number());
    }
    
    @Override
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        // TODO: Implement API call to post inline comments
        logger.debug("Gitea inline comments not yet implemented for PR {}", pull.number());
    }
    
    @Override
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body) {
        // TODO: Implement API call to create/update summary comment
        logger.debug("Gitea summary comment not yet implemented for PR {}", pull.number());
    }
    
    // Helper methods
    
    private ParsedEvent parsePullRequestEvent(JsonNode json) {
        JsonNode action = json.get("action");
        JsonNode pullRequest = json.get("pull_request");
        JsonNode repository = json.get("repository");
        
        if (pullRequest == null || repository == null) {
            throw new GiteaException("parseEvent", "Missing pull_request or repository in payload");
        }
        
        // Determine event type based on action
        String eventType = switch (action.asText()) {
            case "opened" -> "pull_request.opened";
            case "synchronize", "edited" -> "pull_request.synchronize";
            case "closed" -> {
                boolean merged = pullRequest.get("merged").asBoolean(false);
                yield merged ? "pull_request.closed" : "pull_request.closed";
            }
            default -> "pull_request.synchronize";
        };
        
        // Extract repository info
        RepoRef repo = new RepoRef(
            repository.get("owner").get("login").asText(),
            repository.get("name").asText(),
            "gitea",
            repository.get("html_url").asText()
        );
        
        // Extract pull request info
        PullRef pull = new PullRef(
            pullRequest.get("number").asText(),
            pullRequest.get("number").asText(),
            pullRequest.get("title").asText(),
            pullRequest.get("head").get("ref").asText(),
            pullRequest.get("base").get("ref").asText(),
            pullRequest.get("head").get("sha").asText(),
            false // Gitea doesn't have draft concept by default
        );
        
        return new ParsedEvent(eventType, repo, pull);
    }
    
    private ParsedEvent parsePushEvent(JsonNode json) {
        JsonNode repository = json.get("repository");
        
        if (repository == null) {
            throw new GiteaException("parseEvent", "Missing repository in push payload");
        }
        
        RepoRef repo = new RepoRef(
            repository.get("owner").get("login").asText(),
            repository.get("name").asText(),
            "gitea",
            repository.get("html_url").asText()
        );
        
        return new ParsedEvent("push", repo, null);
    }
    
    private boolean verifyHmacSignature(String signature, byte[] payload, String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            return true; // No secret configured, skip verification
        }
        
        try {
            // Parse signature format: "sha1=..." or "sha256=..."
            String[] parts = signature.split("=", 2);
            if (parts.length != 2) {
                return false;
            }
            
            String algorithm = parts[0];
            String expectedSignature = parts[1];
            
            // Compute HMAC
            String hmacAlgorithm = algorithm.equals("sha1") ? "HmacSHA1" : "HmacSHA256";
            String computedSignature = computeHmac(payload, secret, hmacAlgorithm);
            
            return computedSignature.equals(expectedSignature);
            
        } catch (Exception e) {
            logger.error("Error verifying HMAC signature", e);
            return false;
        }
    }
    
    private String computeHmac(byte[] data, String secret, String algorithm) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algorithm);
        javax.crypto.spec.SecretKeySpec secretKeySpec = 
            new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), algorithm);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data);
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
    
    private String extractHost(String url) {
        if (url == null) {
            return null;
        }
        
        try {
            // Remove protocol
            String cleanUrl = url;
            if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                cleanUrl = cleanUrl.substring(cleanUrl.indexOf("://") + 3);
            }
            
            // Extract host part (before first slash or colon)
            int slashIndex = cleanUrl.indexOf('/');
            int colonIndex = cleanUrl.indexOf(':');
            
            int endIndex = cleanUrl.length();
            if (slashIndex > 0) {
                endIndex = Math.min(endIndex, slashIndex);
            }
            if (colonIndex > 0) {
                endIndex = Math.min(endIndex, colonIndex);
            }
            
            return cleanUrl.substring(0, endIndex);
            
        } catch (Exception e) {
            logger.warn("Failed to extract host from URL: {}", url, e);
            return null;
        }
    }
    
    private Instant parseInstant(String dateString) {
        try {
            return Instant.parse(dateString);
        } catch (Exception e) {
            logger.warn("Failed to parse date: {}", dateString);
            return Instant.now();
        }
    }
}
