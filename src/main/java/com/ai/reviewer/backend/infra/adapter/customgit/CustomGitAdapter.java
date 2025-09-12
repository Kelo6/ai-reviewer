package com.ai.reviewer.backend.infra.adapter.customgit;

import com.ai.reviewer.backend.domain.adapter.scm.*;
import com.ai.reviewer.backend.infra.adapter.gitea.GiteaAdapter;
import com.ai.reviewer.backend.infra.adapter.gitlab.GitlabAdapter;
import com.ai.reviewer.backend.infra.adapter.github.GithubAdapter;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Custom Git adapter for self-hosted Git servers.
 * 
 * <p>This adapter acts as a bridge to support custom/self-hosted Git servers
 * by delegating to existing adapters based on the API type configuration.
 * Supports:
 * - Gitea-compatible APIs
 * - GitLab-compatible APIs 
 * - GitHub-compatible APIs
 */
@Component
public class CustomGitAdapter implements ScmAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomGitAdapter.class);
    
    @Value("${ai-reviewer.scm.custom-git.api-base:}")
    private String apiBase;
    
    @Value("${ai-reviewer.scm.custom-git.api-type:gitea}")
    private String apiType;
    
    @Value("${ai-reviewer.scm.custom-git.token:}")
    private String token;
    
    private final GiteaAdapter giteaAdapter;
    private final GitlabAdapter gitlabAdapter;
    private final GithubAdapter githubAdapter;
    
    @Autowired
    public CustomGitAdapter(GiteaAdapter giteaAdapter, 
                           GitlabAdapter gitlabAdapter,
                           GithubAdapter githubAdapter) {
        this.giteaAdapter = giteaAdapter;
        this.gitlabAdapter = gitlabAdapter;
        this.githubAdapter = githubAdapter;
    }
    
    @Override
    public String getProvider() {
        return "custom-git";
    }
    
    @Override
    public boolean supports(RepoRef repo) {
        if (repo == null || repo.provider() == null) {
            return false;
        }
        
        String provider = repo.provider().toLowerCase().trim();
        
        // Support custom-git provider
        if ("custom-git".equals(provider)) {
            return isConfigured();
        }
        
        // Check if repo URL matches configured custom Git instance
        if (isConfigured() && repo.url() != null) {
            String configuredHost = extractHost(apiBase);
            String repoHost = extractHost(repo.url());
            return configuredHost != null && configuredHost.equals(repoHost);
        }
        
        return false;
    }
    
    @Override
    public boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            return delegateAdapter.verifyWebhookSignature(headers, rawBody);
        }
        
        logger.warn("No delegate adapter available for custom Git webhook verification");
        return false;
    }
    
    @Override
    public ParsedEvent parseEvent(byte[] payload, Map<String, String> headers) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            return delegateAdapter.parseEvent(payload, headers);
        }
        
        throw new ScmAdapterException("custom-git", "parseEvent", 
                "No delegate adapter available for API type: " + apiType);
    }
    
    @Override
    public PullRef getPull(RepoRef repo, String number) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            return delegateAdapter.getPull(repo, number);
        }
        
        throw new ScmAdapterException("custom-git", "getPull", 
                "No delegate adapter available for API type: " + apiType);
    }
    
    @Override
    public List<DiffHunk> listDiff(RepoRef repo, PullRef pull) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            return delegateAdapter.listDiff(repo, pull);
        }
        
        throw new ScmAdapterException("custom-git", "listDiff", 
                "No delegate adapter available for API type: " + apiType);
    }
    
    @Override
    public String getFileBlob(RepoRef repo, String sha, String path, Range range) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            return delegateAdapter.getFileBlob(repo, sha, path, range);
        }
        
        throw new ScmAdapterException("custom-git", "getFileBlob", 
                "No delegate adapter available for API type: " + apiType);
    }
    
    @Override
    public void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            delegateAdapter.upsertCheck(repo, pull, summary);
        } else {
            logger.warn("No delegate adapter available for custom Git check update");
        }
    }
    
    @Override
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            delegateAdapter.postInlineComments(repo, pull, comments);
        } else {
            logger.warn("No delegate adapter available for custom Git inline comments");
        }
    }
    
    @Override
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body) {
        ScmAdapter delegateAdapter = getDelegateAdapter();
        if (delegateAdapter != null) {
            delegateAdapter.createOrUpdateSummaryComment(repo, pull, anchorKey, body);
        } else {
            logger.warn("No delegate adapter available for custom Git summary comment");
        }
    }
    
    // Helper methods
    
    /**
     * Get the delegate adapter based on the configured API type.
     */
    private ScmAdapter getDelegateAdapter() {
        if (!isConfigured()) {
            return null;
        }
        
        return switch (apiType.toLowerCase().trim()) {
            case "gitea" -> giteaAdapter;
            case "gitlab" -> gitlabAdapter;
            case "github" -> githubAdapter;
            default -> {
                logger.warn("Unsupported custom Git API type: {}. Defaulting to Gitea.", apiType);
                yield giteaAdapter;
            }
        };
    }
    
    /**
     * Check if custom Git adapter is properly configured.
     */
    private boolean isConfigured() {
        boolean hasApiBase = apiBase != null && !apiBase.trim().isEmpty();
        boolean hasToken = token != null && !token.trim().isEmpty();
        
        if (hasApiBase && hasToken) {
            logger.debug("Custom Git configuration: API base={}, API type={}", apiBase, apiType);
        } else {
            logger.debug("Custom Git not configured - missing API base or token");
        }
        
        return hasApiBase && hasToken;
    }
    
    /**
     * Extract host from URL.
     */
    private String extractHost(String url) {
        if (url == null || url.trim().isEmpty()) {
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
}
