package com.ai.reviewer.backend.infra.adapter.gitlab;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.shared.enums.FileStatus;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GitLab implementation of the SCM adapter interface.
 * 
 * <p>Provides complete GitLab integration including webhook verification,
 * event parsing, merge request analysis, and feedback posting. Supports
 * both GitLab.com and self-hosted GitLab installations.
 * 
 * <p>Authentication uses Personal Access Token. SSL certificate verification
 * can be optionally disabled for self-hosted instances with self-signed certificates.
 */
@Component
public class GitlabAdapter implements ScmAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(GitlabAdapter.class);
    
    private static final String PROVIDER_NAME = "gitlab";
    
    /** GitLab-specific webhook event types */
    private static final String EVENT_MERGE_REQUEST = "Merge Request Hook";
    private static final String EVENT_PUSH = "Push Hook";
    
    /** Merge request actions that should trigger reviews */
    private static final Set<String> REVIEW_TRIGGER_ACTIONS = Set.of(
        "open", "update", "reopen"
    );
    
    /** Maximum file size for content retrieval (1MB) */
    private static final long MAX_FILE_SIZE = 1024 * 1024;
    
    /** Maximum number of files in diff */
    private static final int MAX_DIFF_FILES = 100;
    
    /** Discussion anchor prefix for summary comments */
    private static final String SUMMARY_DISCUSSION_PREFIX = "ai-review-summary-";
    
    private final GitlabConfig config;
    private final GitlabApiClient apiClient;
    private final GitlabWebhookValidator webhookValidator;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public GitlabAdapter(GitlabConfig config, 
                        GitlabApiClient apiClient,
                        GitlabWebhookValidator webhookValidator,
                        ObjectMapper objectMapper) {
        this.config = config;
        this.apiClient = apiClient;
        this.webhookValidator = webhookValidator;
        this.objectMapper = objectMapper;
        
        logger.info("GitLab adapter initialized for: {}", config.getApiBase());
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
            
        } catch (GitlabException e) {
            logger.error("Webhook signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public ParsedEvent parseEvent(byte[] payload, Map<String, String> headers) {
        try {
            String eventType = getEventType(headers);
            JsonNode eventJson = objectMapper.readTree(payload);
            
            logger.debug("Parsing GitLab webhook event: {}", eventType);
            
            return switch (eventType) {
                case EVENT_MERGE_REQUEST -> parseMergeRequestEvent(eventJson);
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
            String projectPath = extractProjectPath(repo);
            Long mergeRequestIid = Long.parseLong(number);
            
            MergeRequest mr = apiClient.getMergeRequest(projectPath, mergeRequestIid);
            return convertMergeRequest(mr);
            
        } catch (NumberFormatException e) {
            throw new GitlabException(GitlabException.ErrorCode.MERGE_REQUEST_NOT_FOUND, 
                "Invalid merge request IID: " + number);
        } catch (GitlabException e) {
            throw e; // Re-throw GitLab exceptions as-is
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.MERGE_REQUEST_NOT_FOUND, 
                "Failed to retrieve merge request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DiffHunk> listDiff(RepoRef repo, PullRef pull) {
        try {
            String projectPath = extractProjectPath(repo);
            Long mergeRequestIid = Long.parseLong(pull.number());
            
            GitLabApi api = apiClient.getGitLabApi();
            
            // Get merge request with changes
            MergeRequest mrWithChanges = api.getMergeRequestApi()
                .getMergeRequestChanges(projectPath, mergeRequestIid);
            
            List<Diff> changes = mrWithChanges.getChanges();
            if (changes == null) {
                return new ArrayList<>();
            }
            
            if (changes.size() > MAX_DIFF_FILES) {
                logger.warn("Merge request has {} files, limiting to {}", changes.size(), MAX_DIFF_FILES);
                changes = changes.subList(0, MAX_DIFF_FILES);
            }
            
            return changes.stream()
                .map(this::convertDiff)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.CHANGES_RETRIEVAL_FAILED, 
                "Failed to retrieve changes: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getFileBlob(RepoRef repo, String sha, String path, Range range) {
        try {
            String projectPath = extractProjectPath(repo);
            GitLabApi api = apiClient.getGitLabApi();
            
            // Get file content at specific commit
            RepositoryFile file = api.getRepositoryFileApi()
                .getFile(projectPath, path, sha);
            
            if (file.getSize() != null && file.getSize() > MAX_FILE_SIZE) {
                throw new GitlabException(GitlabException.ErrorCode.FILE_CONTENT_FAILED, 
                    String.format("File too large: %d bytes (max: %d)", file.getSize(), MAX_FILE_SIZE));
            }
            
            String fullContent = file.getDecodedContentAsString();
            
            // Apply range if specified
            if (range != null) {
                return extractLineRange(fullContent, range);
            }
            
            return fullContent;
            
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.FILE_CONTENT_FAILED, 
                "Failed to retrieve file content: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary) {
        try {
            // TODO: Implement commit status creation when GitLab4J API compatibility is resolved
            // For now, create a summary comment instead
            String statusComment = String.format("## AI Code Review Status\n\n" +
                "**Status**: %s\n" +
                "**Summary**: %s\n" +
                "%s", 
                summary.conclusion().toUpperCase(),
                summary.title(),
                summary.hasDetailsUrl() ? String.format("[View Details](%s)", summary.detailsUrl()) : "");
            
            createOrUpdateSummaryComment(repo, pull, "ai-review-status", statusComment);
            
            logger.debug("Created/updated check status comment for MR !{} with conclusion: {}", 
                pull.number(), summary.conclusion());
            
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.STATUS_UPDATE_FAILED, 
                "Failed to create/update commit status: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        if (comments.isEmpty()) {
            return;
        }
        
        try {
            String projectPath = extractProjectPath(repo);
            Long mergeRequestIid = Long.parseLong(pull.number());
            GitLabApi api = apiClient.getGitLabApi();
            
            for (InlineComment comment : comments) {
                if (!comment.hasValidContent()) {
                    continue;
                }
                
                try {
                    // Create simple note (positioned comments are complex in GitLab4J)
                    String noteBody = String.format("**File: %s, Line: %d**\n\n%s", 
                        comment.file(), comment.line(), comment.getSanitizedBody());
                    
                    api.getNotesApi().createMergeRequestNote(
                        projectPath, mergeRequestIid, noteBody);
                    
                } catch (Exception e) {
                    logger.warn("Failed to post inline comment on {}:{} - {}", 
                        comment.file(), comment.line(), e.getMessage());
                    // Continue with other comments
                }
            }
            
            logger.debug("Posted {} inline comments on MR !{}", comments.size(), pull.number());
            
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.DISCUSSION_FAILED, 
                "Failed to post inline comments: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body) {
        try {
            String projectPath = extractProjectPath(repo);
            Long mergeRequestIid = Long.parseLong(pull.number());
            GitLabApi api = apiClient.getGitLabApi();
            
            // Look for existing discussion with anchor
            String anchorPattern = SUMMARY_DISCUSSION_PREFIX + anchorKey;
            Discussion existingDiscussion = null;
            
            List<Discussion> discussions = api.getDiscussionsApi()
                .getMergeRequestDiscussions(projectPath, mergeRequestIid);
            
            for (Discussion discussion : discussions) {
                if (discussion.getNotes() != null && !discussion.getNotes().isEmpty()) {
                    Note firstNote = discussion.getNotes().get(0);
                    if (firstNote.getBody() != null && firstNote.getBody().contains(anchorPattern)) {
                        existingDiscussion = discussion;
                        break;
                    }
                }
            }
            
            String commentBody = "<!-- " + anchorPattern + " -->\n\n" + body;
            
            if (existingDiscussion != null && !existingDiscussion.getNotes().isEmpty()) {
                // Update existing discussion by adding a new note
                Note firstNote = existingDiscussion.getNotes().get(0);
                api.getNotesApi().updateMergeRequestNote(
                    projectPath, mergeRequestIid, firstNote.getId(), commentBody);
                logger.debug("Updated summary discussion on MR !{}", pull.number());
            } else {
                // Create new discussion (simple note without position)
                api.getNotesApi().createMergeRequestNote(
                    projectPath, mergeRequestIid, commentBody);
                logger.debug("Created summary discussion on MR !{}", pull.number());
            }
            
        } catch (Exception e) {
            throw new GitlabException(GitlabException.ErrorCode.DISCUSSION_FAILED, 
                "Failed to create/update summary comment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract event type from webhook headers.
     */
    private String getEventType(Map<String, String> headers) {
        return webhookValidator.getEventType(headers);
    }
    
    /**
     * Parse merge request webhook event.
     */
    private ParsedEvent parseMergeRequestEvent(JsonNode eventJson) {
        JsonNode objectAttributes = eventJson.path("object_attributes");
        String action = objectAttributes.path("action").asText();
        
        String eventType = EVENT_MERGE_REQUEST.toLowerCase().replace(" ", "_") + "." + action;
        
        RepoRef repo = extractRepository(eventJson);
        PullRef pull = extractMergeRequest(objectAttributes);
        
        return new ParsedEvent(eventType, repo, pull);
    }
    
    /**
     * Parse push webhook event.
     */
    private ParsedEvent parsePushEvent(JsonNode eventJson) {
        RepoRef repo = extractRepository(eventJson);
        return new ParsedEvent("push", repo, null);
    }
    
    /**
     * Extract repository information from webhook payload.
     */
    private RepoRef extractRepository(JsonNode eventJson) {
        JsonNode project = eventJson.path("project");
        if (project.isMissingNode()) {
            project = eventJson.path("repository"); // Alternative location
        }
        
        String namespace = project.path("namespace").asText();
        String name = project.path("name").asText();
        String url = project.path("web_url").asText();
        
        // Handle cases where namespace might be in path_with_namespace
        String pathWithNamespace = project.path("path_with_namespace").asText();
        if (StringUtils.hasText(pathWithNamespace) && pathWithNamespace.contains("/")) {
            String[] parts = pathWithNamespace.split("/", 2);
            if (parts.length == 2) {
                namespace = parts[0];
                name = parts[1];
            }
        }
        
        return new RepoRef(PROVIDER_NAME, namespace, name, url);
    }
    
    /**
     * Extract merge request information from webhook payload.
     */
    private PullRef extractMergeRequest(JsonNode mrNode) {
        String id = String.valueOf(mrNode.path("id").asLong());
        String number = String.valueOf(mrNode.path("iid").asInt()); // GitLab uses IID for MR number
        String sourceBranch = mrNode.path("source_branch").asText();
        String targetBranch = mrNode.path("target_branch").asText();
        String sha = mrNode.path("last_commit").path("id").asText();
        boolean draft = "draft".equals(mrNode.path("state").asText()) || 
                       mrNode.path("work_in_progress").asBoolean(false);
        
        return new PullRef(id, number, "GitLab MR", sourceBranch, targetBranch, sha, draft);
    }
    
    /**
     * Convert GitLab merge request to our PullRef format.
     */
    private PullRef convertMergeRequest(MergeRequest mr) {
        return new PullRef(
            String.valueOf(mr.getId()),
            String.valueOf(mr.getIid()),
            mr.getTitle() != null ? mr.getTitle() : "GitLab MR",
            mr.getSourceBranch(),
            mr.getTargetBranch(),
            mr.getSha(),
            mr.getWorkInProgress() || "draft".equals(mr.getState())
        );
    }
    
    /**
     * Convert GitLab diff to our DiffHunk format.
     */
    private DiffHunk convertDiff(Diff diff) {
        FileStatus status = mapFileStatus(diff);
        String oldPath = diff.getOldPath();
        
        return new DiffHunk(
            diff.getNewPath(),
            status,
            diff.getDiff(),
            oldPath,
            0,  // linesAdded placeholder
            0   // linesDeleted placeholder
        );
    }
    
    /**
     * Map GitLab diff to our FileStatus enum.
     */
    private FileStatus mapFileStatus(Diff diff) {
        if (diff.getDeletedFile()) {
            return FileStatus.DELETED;
        } else if (diff.getNewFile()) {
            return FileStatus.ADDED;
        } else if (diff.getRenamedFile()) {
            return FileStatus.RENAMED;
        } else {
            return FileStatus.MODIFIED;
        }
    }
    
    /**
     * Map our conclusion to GitLab commit status state.
     */
    private String mapCommitState(String conclusion) {
        return switch (conclusion.toLowerCase()) {
            case "success" -> "success";
            case "failure" -> "failed";
            case "error" -> "failed";
            case "cancelled" -> "canceled";
            case "pending", "in_progress" -> "pending";
            default -> "pending";
        };
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
     * Extract project path from repository reference.
     */
    private String extractProjectPath(RepoRef repo) {
        return repo.owner() + "/" + repo.name();
    }
}
