package com.ai.reviewer.backend.infra.adapter.bitbucket;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.enums.FileStatus;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bitbucket SCM adapter implementation.
 * 
 * Supports Bitbucket Cloud repositories using REST API v2.0.
 */
@Component
public class BitbucketAdapter implements ScmAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(BitbucketAdapter.class);
    
    private final BitbucketConfig config;
    private final BitbucketApiClient apiClient;
    private final BitbucketWebhookValidator webhookValidator;
    private final ObjectMapper objectMapper;
    
    // Diff parsing patterns
    private static final Pattern DIFF_HEADER_PATTERN = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");
    
    @Autowired
    public BitbucketAdapter(BitbucketConfig config, BitbucketApiClient apiClient, 
                           BitbucketWebhookValidator webhookValidator, ObjectMapper objectMapper) {
        this.config = config;
        this.apiClient = apiClient;
        this.webhookValidator = webhookValidator;
        this.objectMapper = objectMapper;
        
        if (config.isConfigured()) {
            logger.info("Bitbucket adapter initialized for: {}", config.getApiBase());
        }
    }
    
    @Override
    public String getProvider() {
        return "bitbucket";
    }
    
    @Override
    public boolean supports(RepoRef repo) {
        if (repo == null || repo.provider() == null) {
            return false;
        }
        
        String provider = repo.provider().toLowerCase().trim();
        return "bitbucket".equals(provider) || "bitbucket.org".equals(provider);
    }
    
    @Override
    public boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody) {
        try {
            return webhookValidator.validate(headers, rawBody, config.getWebhookSecret());
        } catch (Exception e) {
            logger.error("Failed to verify Bitbucket webhook signature", e);
            return false;
        }
    }
    
    @Override
    public ParsedEvent parseEvent(byte[] payload, Map<String, String> headers) {
        try {
            String eventType = headers.get("X-Event-Key");
            if (eventType == null) {
                throw new BitbucketException("parseEvent", "Missing X-Event-Key header");
            }
            
            JsonNode json = objectMapper.readTree(payload);
            
            // Handle pull request events
            if (eventType.startsWith("pullrequest:")) {
                return parsePullRequestEvent(json, eventType);
            }
            
            // Handle push events
            if (eventType.equals("repo:push")) {
                return parsePushEvent(json);
            }
            
            // Unsupported event type
            logger.debug("Unsupported Bitbucket event type: {}", eventType);
            return new ParsedEvent("unknown", null, null);
            
        } catch (Exception e) {
            throw new BitbucketException("parseEvent", "Failed to parse webhook payload", e);
        }
    }
    
    @Override
    public PullRef getPull(RepoRef repo, String number) {
        try {
            JsonNode pr = apiClient.getPullRequest(repo.owner(), repo.name(), number);
            return convertToPullRef(pr, repo);
            
        } catch (Exception e) {
            throw new BitbucketException("getPull", 
                String.format("Failed to get pull request %s/%s#%s", repo.owner(), repo.name(), number), e);
        }
    }
    
    @Override
    public List<DiffHunk> listDiff(RepoRef repo, PullRef pull) {
        try {
            String diffText = apiClient.getPullRequestDiff(repo.owner(), repo.name(), pull.number());
            return parseDiff(diffText);
            
        } catch (Exception e) {
            throw new BitbucketException("listDiff", 
                String.format("Failed to get diff for pull request %s/%s#%s", 
                    repo.owner(), repo.name(), pull.number()), e);
        }
    }
    
    @Override
    public String getFileBlob(RepoRef repo, String sha, String path, Range range) {
        try {
            String content = apiClient.getFileContent(repo.owner(), repo.name(), sha, path);
            
            if (range != null) {
                return extractLineRange(content, range);
            }
            
            return content;
            
        } catch (Exception e) {
            throw new BitbucketException("getFileBlob", 
                String.format("Failed to get file content %s/%s:%s@%s", 
                    repo.owner(), repo.name(), path, sha), e);
        }
    }
    
    @Override
    public void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary) {
        try {
            // Convert CheckSummary conclusion to Bitbucket build status
            String state = convertCheckStatus(summary.conclusion());
            String description = summary.title();
            if (description.length() > 255) {
                description = description.substring(0, 252) + "...";
            }
            
            apiClient.updateBuildStatus(
                repo.owner(),
                repo.name(),
                pull.sha(),
                state,
                "ai-reviewer",
                "AI Code Review",
                summary.detailsUrl(),
                description
            );
            
            logger.debug("Updated Bitbucket build status for PR {}: {}", pull.number(), state);
            
        } catch (Exception e) {
            throw new BitbucketException("upsertCheck", 
                String.format("Failed to update check status for %s/%s#%s", 
                    repo.owner(), repo.name(), pull.number()), e);
        }
    }
    
    @Override
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        try {
            for (InlineComment comment : comments) {
                apiClient.createInlineComment(
                    repo.owner(),
                    repo.name(),
                    pull.number(),
                    comment.file(),
                    comment.line(),
                    comment.body()
                );
            }
            
            logger.debug("Posted {} inline comments for PR {}", comments.size(), pull.number());
            
        } catch (Exception e) {
            throw new BitbucketException("postInlineComments", 
                String.format("Failed to post inline comments for %s/%s#%s", 
                    repo.owner(), repo.name(), pull.number()), e);
        }
    }
    
    @Override
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body) {
        try {
            // Check for existing summary comment
            JsonNode existingComments = apiClient.getPullRequestComments(repo.owner(), repo.name(), pull.number());
            
            String commentWithAnchor = String.format("<!-- %s -->\n%s", anchorKey, body);
            
            // Look for existing comment with the same anchor
            String existingCommentId = findCommentByAnchor(existingComments, anchorKey);
            
            if (existingCommentId != null) {
                // Delete existing comment and create new one (Bitbucket doesn't support comment updates)
                apiClient.deleteComment(repo.owner(), repo.name(), pull.number(), existingCommentId);
            }
            
            // Create new comment
            apiClient.createPullRequestComment(repo.owner(), repo.name(), pull.number(), commentWithAnchor);
            
            logger.debug("Created/updated summary comment for PR {}", pull.number());
            
        } catch (Exception e) {
            throw new BitbucketException("createOrUpdateSummaryComment", 
                String.format("Failed to create/update summary comment for %s/%s#%s", 
                    repo.owner(), repo.name(), pull.number()), e);
        }
    }
    
    // Helper methods
    
    private ParsedEvent parsePullRequestEvent(JsonNode json, String eventType) {
        JsonNode pullRequest = json.get("pullrequest");
        JsonNode repository = json.get("repository");
        
        if (pullRequest == null || repository == null) {
            throw new BitbucketException("parseEvent", "Missing pullrequest or repository in payload");
        }
        
        // Determine event type
        String type = switch (eventType) {
            case "pullrequest:created" -> "pull_request.opened";
            case "pullrequest:updated" -> "pull_request.synchronize";
            case "pullrequest:fulfilled" -> "pull_request.closed";
            case "pullrequest:rejected" -> "pull_request.closed";
            default -> "pull_request.synchronize";
        };
        
        // Extract repository info
        RepoRef repo = new RepoRef(
            repository.get("full_name").asText().split("/")[0],
            repository.get("name").asText(),
            "bitbucket",
            repository.get("links").get("html").get("href").asText()
        );
        
        // Extract pull request info
        PullRef pull = convertToPullRef(pullRequest, repo);
        
        return new ParsedEvent(type, repo, pull);
    }
    
    private ParsedEvent parsePushEvent(JsonNode json) {
        JsonNode repository = json.get("repository");
        
        if (repository == null) {
            throw new BitbucketException("parseEvent", "Missing repository in push payload");
        }
        
        RepoRef repo = new RepoRef(
            repository.get("full_name").asText().split("/")[0],
            repository.get("name").asText(),
            "bitbucket",
            repository.get("links").get("html").get("href").asText()
        );
        
        return new ParsedEvent("push", repo, null);
    }
    
    private PullRef convertToPullRef(JsonNode pr, RepoRef repo) {
        return new PullRef(
            pr.get("id").asText(),
            pr.get("id").asText(), // number
            pr.get("title").asText(),
            pr.get("source").get("branch").get("name").asText(),
            pr.get("destination").get("branch").get("name").asText(),
            pr.get("source").get("commit").get("hash").asText(),
            false // Bitbucket doesn't have draft concept
        );
    }
    
    private List<DiffHunk> parseDiff(String diffText) {
        List<DiffHunk> hunks = new ArrayList<>();
        String[] lines = diffText.split("\n");
        
        String currentFile = null;
        List<String> currentLines = new ArrayList<>();
        int oldStart = 0, newStart = 0;
        
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // Save previous hunk if exists
                if (currentFile != null && !currentLines.isEmpty()) {
                    hunks.add(new DiffHunk(currentFile, FileStatus.MODIFIED, "", String.join("\n", currentLines), oldStart, newStart));
                }
                
                // Parse new file
                Matcher matcher = DIFF_HEADER_PATTERN.matcher(line);
                if (matcher.matches()) {
                    currentFile = matcher.group(2);
                }
                currentLines.clear();
            } else if (line.startsWith("@@")) {
                // Save previous hunk if exists
                if (currentFile != null && !currentLines.isEmpty()) {
                    hunks.add(new DiffHunk(currentFile, FileStatus.MODIFIED, "", String.join("\n", currentLines), oldStart, newStart));
                }
                
                // Parse hunk header
                Matcher matcher = HUNK_HEADER_PATTERN.matcher(line);
                if (matcher.matches()) {
                    oldStart = Integer.parseInt(matcher.group(1));
                    newStart = Integer.parseInt(matcher.group(3));
                }
                currentLines.clear();
                currentLines.add(line);
            } else if (currentFile != null) {
                currentLines.add(line);
            }
        }
        
        // Save last hunk
        if (currentFile != null && !currentLines.isEmpty()) {
            hunks.add(new DiffHunk(currentFile, FileStatus.MODIFIED, "", String.join("\n", currentLines), oldStart, newStart));
        }
        
        return hunks;
    }
    
    private String extractLineRange(String content, Range range) {
        String[] lines = content.split("\n");
        int startLine = Math.max(0, range.startLine() - 1);
        int endLine = Math.min(lines.length, range.endLine());
        
        if (startLine >= endLine) {
            return "";
        }
        
        return String.join("\n", Arrays.copyOfRange(lines, startLine, endLine));
    }
    
    private String convertCheckStatus(String conclusion) {
        return switch (conclusion) {
            case "success" -> "SUCCESSFUL";
            case "failure" -> "FAILED";
            case "in_progress" -> "INPROGRESS";
            case "cancelled" -> "STOPPED";
            default -> "INPROGRESS";
        };
    }
    
    private String findCommentByAnchor(JsonNode comments, String anchorKey) {
        if (comments.has("values")) {
            for (JsonNode comment : comments.get("values")) {
                JsonNode content = comment.get("content");
                if (content != null && content.has("raw")) {
                    String body = content.get("raw").asText();
                    if (body.contains("<!-- " + anchorKey + " -->")) {
                        return comment.get("id").asText();
                    }
                }
            }
        }
        return null;
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
