package com.ai.reviewer.backend.infra.adapter.github;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.enums.FileStatus;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GitHub implementation of the SCM adapter interface.
 * 
 * <p>Provides complete GitHub integration including webhook verification,
 * event parsing, pull request analysis, and feedback posting. Supports
 * both GitHub.com and GitHub Enterprise Server deployments.
 * 
 * <p>Authentication supports both Personal Access Token and GitHub App modes.
 * GitHub App authentication is preferred for production deployments as it
 * provides higher rate limits and better security.
 */
@Component
public class GithubAdapter implements ScmAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GithubAdapter.class);
    
    private static final String PROVIDER_NAME = "github";
    
    /** GitHub-specific webhook event types */
    private static final String EVENT_PULL_REQUEST = "pull_request";
    private static final String EVENT_PUSH = "push";
    
    /** Pull request actions that should trigger reviews */
    private static final Set<String> REVIEW_TRIGGER_ACTIONS = Set.of(
        "opened", "synchronize", "reopened"
    );
    
    /** Check run name for AI code reviews */
    private static final String CHECK_RUN_NAME = "AI Code Review";
    
    /** Maximum file size for content retrieval (1MB) */
    private static final long MAX_FILE_SIZE = 1024 * 1024;
    
    /** Maximum number of files in diff */
    private static final int MAX_DIFF_FILES = 100;
    
    private final GithubConfig config;
    private final GithubApiClient apiClient;
    private final GithubWebhookValidator webhookValidator;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public GithubAdapter(GithubConfig config, 
                        GithubApiClient apiClient,
                        GithubWebhookValidator webhookValidator,
                        ObjectMapper objectMapper) {
        this.config = config;
        this.apiClient = apiClient;
        this.webhookValidator = webhookValidator;
        this.objectMapper = objectMapper;
        
        logger.info("GitHub adapter initialized for: {}", config.getApiBase());
    }
    
    @Override
    public String getProvider() {
        return PROVIDER_NAME;
    }
    
    @Override
    public boolean supports(RepoRef repo) {
        return PROVIDER_NAME.equalsIgnoreCase(repo.provider());
    }
    
    @Override
    public boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody) {
        try {
            if (!config.hasWebhookSecret()) {
                logger.warn("Webhook secret not configured - signature verification skipped");
                return true; // Allow webhooks without signature if secret not configured
            }
            
            return webhookValidator.verifySignature(headers, rawBody, config.getWebhookSecret());
            
        } catch (GithubException e) {
            logger.error("Webhook signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public ParsedEvent parseEvent(byte[] payload, Map<String, String> headers) {
        try {
            String eventType = getEventType(headers);
            JsonNode eventJson = objectMapper.readTree(payload);
            
            logger.debug("Parsing GitHub webhook event: {}", eventType);
            
            return switch (eventType) {
                case EVENT_PULL_REQUEST -> parsePullRequestEvent(eventJson);
                case EVENT_PUSH -> parsePushEvent(eventJson);
                default -> {
                    logger.debug("Unsupported event type: {}", eventType);
                    yield new ParsedEvent(eventType, extractRepository(eventJson), null);
                }
            };
            
        } catch (Exception e) {
            throw new ScmAdapterException(PROVIDER_NAME, "parseEvent", 
                "Failed to parse webhook payload: " + e.getMessage(), e);
        }
    }
    
    @Override
    public PullRef getPull(RepoRef repo, String number) {
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            GHPullRequest pr = ghRepo.getPullRequest(Integer.parseInt(number));
            
            return convertPullRequest(pr);
            
        } catch (IOException e) {
            if (isNotFoundError(e)) {
                throw GithubException.pullRequestNotFound(repo.owner(), repo.name(), number);
            }
            throw new GithubException(GithubException.ErrorCode.PULL_REQUEST_NOT_FOUND, 
                "Failed to retrieve pull request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DiffHunk> listDiff(RepoRef repo, PullRef pull) {
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            GHPullRequest pr = ghRepo.getPullRequest(Integer.parseInt(pull.number()));
            
            List<GHPullRequestFileDetail> files = pr.listFiles().toList();
            
            if (files.size() > MAX_DIFF_FILES) {
                logger.warn("Pull request has {} files, limiting to {}", files.size(), MAX_DIFF_FILES);
                files = files.subList(0, MAX_DIFF_FILES);
            }
            
            return files.stream()
                .map(this::convertFileDetail)
                .collect(Collectors.toList());
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.DIFF_RETRIEVAL_FAILED, 
                "Failed to retrieve diff: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getFileBlob(RepoRef repo, String sha, String path, Range range) {
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            
            // Get file content at specific commit
            GHContent content = ghRepo.getFileContent(path, sha);
            
            // Check file size
            if (content.getSize() > MAX_FILE_SIZE) {
                throw new GithubException(GithubException.ErrorCode.FILE_CONTENT_FAILED, 
                    String.format("File too large: %d bytes (max: %d)", content.getSize(), MAX_FILE_SIZE));
            }
            
            String fullContent = content.getContent();
            
            // Apply range if specified
            if (range != null) {
                return extractLineRange(fullContent, range);
            }
            
            return fullContent;
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.FILE_CONTENT_FAILED, 
                "Failed to retrieve file content: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary) {
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            
            // Create or update check run
            GHCheckRunBuilder checkBuilder = ghRepo.createCheckRun(CHECK_RUN_NAME, pull.sha());
            
            // Set conclusion and status
            GHCheckRun.Conclusion conclusion = mapConclusion(summary.conclusion());
            GHCheckRun.Status status = conclusion == null ? GHCheckRun.Status.IN_PROGRESS : GHCheckRun.Status.COMPLETED;
            
            checkBuilder.withStatus(status);
            if (conclusion != null) {
                checkBuilder.withConclusion(conclusion);
            }
            
            // Set details URL if provided
            if (summary.hasDetailsUrl()) {
                checkBuilder.withDetailsURL(summary.detailsUrl());
            }
            
            // Create the check run
            GHCheckRun checkRun = checkBuilder.create();
            
            logger.debug("Created/updated check run {} for PR #{} with conclusion: {}", 
                checkRun.getId(), pull.number(), summary.conclusion());
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.CHECK_RUN_FAILED, 
                "Failed to create/update check run: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        if (comments.isEmpty()) {
            return;
        }
        
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            GHPullRequest pr = ghRepo.getPullRequest(Integer.parseInt(pull.number()));
            
            for (InlineComment comment : comments) {
                if (!comment.hasValidContent()) {
                    continue;
                }
                
                try {
                    // Create review comment
                    GHPullRequestReviewBuilder reviewBuilder = pr.createReview();
                    
                    // Map side to GitHub comment side
                    GHPullRequestReviewComment.Side side = mapCommentSide(comment.side());
                    
                    // Create single-line review comment (GitHub API limitation)
                    reviewBuilder.comment(comment.getSanitizedBody(), comment.file(), 
                        comment.line());
                    
                    reviewBuilder.create();
                    
                } catch (IOException e) {
                    logger.warn("Failed to post inline comment on {}:{} - {}", 
                        comment.file(), comment.line(), e.getMessage());
                    // Continue with other comments
                }
            }
            
            logger.debug("Posted {} inline comments on PR #{}", comments.size(), pull.number());
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.COMMENT_FAILED, 
                "Failed to post inline comments: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body) {
        try {
            GitHub github = getGitHubForRepo(repo);
            GHRepository ghRepo = github.getRepository(repo.owner() + "/" + repo.name());
            GHPullRequest pr = ghRepo.getPullRequest(Integer.parseInt(pull.number()));
            
            // Look for existing comment with anchor
            String anchorPattern = "<!-- " + anchorKey + " -->";
            GHIssueComment existingComment = null;
            
            for (GHIssueComment comment : pr.listComments()) {
                if (comment.getBody().contains(anchorPattern)) {
                    existingComment = comment;
                    break;
                }
            }
            
            String commentBody = anchorPattern + "\n\n" + body;
            
            if (existingComment != null) {
                // Update existing comment
                existingComment.update(commentBody);
                logger.debug("Updated summary comment on PR #{}", pull.number());
            } else {
                // Create new comment
                pr.comment(commentBody);
                logger.debug("Created summary comment on PR #{}", pull.number());
            }
            
        } catch (IOException e) {
            throw new GithubException(GithubException.ErrorCode.COMMENT_FAILED, 
                "Failed to create/update summary comment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get GitHub client for repository (handles installation detection for GitHub Apps).
     */
    private GitHub getGitHubForRepo(RepoRef repo) throws GithubException {
        // For GitHub App auth, we would need to determine the installation ID
        // This is simplified - in practice, you'd store or look up installation mappings
        return apiClient.getGitHub(null);
    }
    
    /**
     * Extract event type from webhook headers.
     */
    private String getEventType(Map<String, String> headers) {
        // GitHub sends event type in X-GitHub-Event header
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("X-GitHub-Event".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "unknown";
    }
    
    /**
     * Parse pull request webhook event.
     */
    private ParsedEvent parsePullRequestEvent(JsonNode eventJson) {
        String action = eventJson.path("action").asText();
        String eventType = EVENT_PULL_REQUEST + "." + action;
        
        RepoRef repo = extractRepository(eventJson);
        PullRef pull = extractPullRequest(eventJson.path("pull_request"));
        
        return new ParsedEvent(eventType, repo, pull);
    }
    
    /**
     * Parse push webhook event.
     */
    private ParsedEvent parsePushEvent(JsonNode eventJson) {
        RepoRef repo = extractRepository(eventJson);
        return new ParsedEvent(EVENT_PUSH, repo, null);
    }
    
    /**
     * Extract repository information from webhook payload.
     */
    private RepoRef extractRepository(JsonNode eventJson) {
        JsonNode repoNode = eventJson.path("repository");
        
        String owner = repoNode.path("owner").path("login").asText();
        String name = repoNode.path("name").asText();
        String url = repoNode.path("html_url").asText();
        
        return new RepoRef(PROVIDER_NAME, owner, name, url);
    }
    
    /**
     * Extract pull request information from webhook payload.
     */
    private PullRef extractPullRequest(JsonNode prNode) {
        String id = String.valueOf(prNode.path("id").asLong());
        String number = String.valueOf(prNode.path("number").asInt());
        String sourceBranch = prNode.path("head").path("ref").asText();
        String targetBranch = prNode.path("base").path("ref").asText();
        String sha = prNode.path("head").path("sha").asText();
        boolean draft = prNode.path("draft").asBoolean(false);
        
        return new PullRef(id, number, "GitHub PR", sourceBranch, targetBranch, sha, draft);
    }
    
    /**
     * Convert GitHub pull request to our PullRef format.
     */
    private PullRef convertPullRequest(GHPullRequest pr) throws IOException {
        return new PullRef(
            String.valueOf(pr.getId()),
            String.valueOf(pr.getNumber()),
            pr.getTitle() != null ? pr.getTitle() : "GitHub PR",
            pr.getHead().getRef(),
            pr.getBase().getRef(),
            pr.getHead().getSha(),
            pr.isDraft()
        );
    }
    
    /**
     * Convert GitHub file detail to our DiffHunk format.
     */
    private DiffHunk convertFileDetail(GHPullRequestFileDetail file) {
        FileStatus status = mapFileStatus(file.getStatus());
        String oldPath = file.getPreviousFilename();
        
        return new DiffHunk(
            file.getFilename(),
            status,
            file.getPatch(),
            oldPath,
            0,  // linesAdded placeholder
            0   // linesDeleted placeholder
        );
    }
    
    /**
     * Map GitHub file status to our FileStatus enum.
     */
    private FileStatus mapFileStatus(String githubStatus) {
        return switch (githubStatus.toLowerCase()) {
            case "added" -> FileStatus.ADDED;
            case "removed" -> FileStatus.DELETED;
            case "renamed" -> FileStatus.RENAMED;
            case "modified" -> FileStatus.MODIFIED;
            default -> FileStatus.MODIFIED;
        };
    }
    
    /**
     * Map our conclusion to GitHub check run conclusion.
     */
    private GHCheckRun.Conclusion mapConclusion(String conclusion) {
        return switch (conclusion.toLowerCase()) {
            case "success" -> GHCheckRun.Conclusion.SUCCESS;
            case "failure" -> GHCheckRun.Conclusion.FAILURE;
            case "neutral" -> GHCheckRun.Conclusion.NEUTRAL;
            case "cancelled" -> GHCheckRun.Conclusion.CANCELLED;
            case "error" -> GHCheckRun.Conclusion.FAILURE;
            default -> null; // Will be treated as in-progress
        };
    }
    
    /**
     * Map our comment side to GitHub comment side.
     */
    private GHPullRequestReviewComment.Side mapCommentSide(String side) {
        if ("LEFT".equalsIgnoreCase(side)) {
            return GHPullRequestReviewComment.Side.LEFT;
        } else {
            return GHPullRequestReviewComment.Side.RIGHT;
        }
    }
    
    /**
     * Extract line range from file content.
     */
    private String extractLineRange(String content, Range range) {
        String[] lines = content.split("\\r?\\n");
        
        int startIndex = Math.max(0, range.startLine() - 1);
        int endIndex = Math.min(lines.length, range.endLine());
        
        if (startIndex >= lines.length) {
            return "";
        }
        
        return String.join("\n", Arrays.copyOfRange(lines, startIndex, endIndex));
    }
    
    /**
     * Check if exception indicates a "not found" error.
     */
    private boolean isNotFoundError(IOException e) {
        return e.getMessage() != null && 
               (e.getMessage().contains("404") || e.getMessage().toLowerCase().contains("not found"));
    }
}
