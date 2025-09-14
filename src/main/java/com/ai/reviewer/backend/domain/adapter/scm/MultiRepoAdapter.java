package com.ai.reviewer.backend.domain.adapter.scm;

import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多仓库适配器统一管理器
 * 
 * <p>提供统一的接口来处理多种Git平台的仓库操作，包括：
 * - GitHub (github.com)
 * - GitLab (gitlab.com 和自建实例)
 * - Bitbucket (bitbucket.org)
 * - Gitea (自建实例)
 * - 自建Git服务器
 * 
 * <p>自动根据仓库URL或provider字段路由到对应的适配器实现。
 */
@Component
public class MultiRepoAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiRepoAdapter.class);
    
    private final ScmAdapterRouter adapterRouter;
    
    @Autowired
    public MultiRepoAdapter(ScmAdapterRouter adapterRouter) {
        this.adapterRouter = adapterRouter;
        
        logger.info("Multi-repo adapter initialized with {} providers: {}", 
                adapterRouter.getAdapterCount(), 
                adapterRouter.getSupportedProviders());
    }
    
    /**
     * 自动检测仓库provider并验证webhook签名
     */
    public boolean verifyWebhookSignature(String repoUrl, Map<String, String> headers, byte[] rawBody) {
        RepoRef repo = detectRepoProvider(repoUrl, headers);
        return adapterRouter.verifyWebhookSignature(repo, headers, rawBody);
    }
    
    /**
     * 解析webhook事件
     */
    public ParsedEvent parseWebhookEvent(String repoUrl, byte[] payload, Map<String, String> headers) {
        RepoRef repo = detectRepoProvider(repoUrl, headers);
        return adapterRouter.parseEvent(repo, payload, headers);
    }
    
    /**
     * 获取Pull Request详情
     */
    public PullRef getPullRequest(RepoRef repo, String pullNumber) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        return adapter.getPull(repo, pullNumber);
    }
    
    /**
     * 获取Pull Request的diff信息
     */
    public List<DiffHunk> getPullRequestDiff(RepoRef repo, PullRef pull) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        return adapter.listDiff(repo, pull);
    }
    
    /**
     * 获取文件内容
     */
    public String getFileContent(RepoRef repo, String sha, String path, Range range) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        return adapter.getFileBlob(repo, sha, path, range);
    }
    
    /**
     * 更新检查状态
     */
    public void updateCheckStatus(RepoRef repo, PullRef pull, CheckSummary summary) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        adapter.upsertCheck(repo, pull, summary);
    }
    
    /**
     * 发布内联评论
     */
    public void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        adapter.postInlineComments(repo, pull, comments);
    }
    
    /**
     * 创建或更新总结评论
     */
    public void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String summary) {
        ScmAdapter adapter = adapterRouter.getAdapter(repo);
        adapter.createOrUpdateSummaryComment(repo, pull, anchorKey, summary);
    }
    
    /**
     * 检查仓库是否支持
     */
    public boolean isRepositorySupported(RepoRef repo) {
        return adapterRouter.isSupported(repo);
    }
    
    /**
     * 检查仓库URL是否支持
     */
    public boolean isRepositorySupported(String repoUrl) {
        try {
            RepoRef repo = parseRepoUrl(repoUrl);
            return adapterRouter.isSupported(repo);
        } catch (Exception e) {
            logger.debug("Failed to parse repository URL: {}", repoUrl, e);
            return false;
        }
    }
    
    /**
     * 获取支持的提供商列表
     */
    public Set<String> getSupportedProviders() {
        return adapterRouter.getSupportedProviders();
    }
    
    /**
     * 获取适配器健康状态
     */
    public Map<String, Boolean> getAdapterHealthStatus() {
        return adapterRouter.healthCheck();
    }
    
    /**
     * 获取适配器详细信息
     */
    public Map<String, String> getAdapterInfo() {
        return adapterRouter.getAdapterInfo();
    }
    
    // Helper methods
    
    /**
     * 从webhook头信息和URL检测仓库提供商
     */
    private RepoRef detectRepoProvider(String repoUrl, Map<String, String> headers) {
        // 首先尝试从webhook头信息检测
        RepoRef repoFromHeaders = detectFromWebhookHeaders(headers);
        if (repoFromHeaders != null) {
            return repoFromHeaders;
        }
        
        // 如果无法从头信息检测，尝试解析URL
        if (repoUrl != null && !repoUrl.trim().isEmpty()) {
            return parseRepoUrl(repoUrl);
        }
        
        throw new ScmAdapterException("unknown", "detectProvider", 
                "Unable to detect repository provider from URL or headers");
    }
    
    /**
     * 从webhook头信息检测仓库提供商
     */
    private RepoRef detectFromWebhookHeaders(Map<String, String> headers) {
        // GitHub webhook headers
        if (headers.containsKey("X-GitHub-Event")) {
            return extractRepoFromGitHubHeaders(headers);
        }
        
        // GitLab webhook headers
        if (headers.containsKey("X-Gitlab-Event")) {
            return extractRepoFromGitLabHeaders(headers);
        }
        
        // Bitbucket webhook headers
        if (headers.containsKey("X-Event-Key")) {
            return extractRepoFromBitbucketHeaders(headers);
        }
        
        // Gitea webhook headers (similar to GitHub)
        if (headers.containsKey("X-Gitea-Event")) {
            return extractRepoFromGiteaHeaders(headers);
        }
        
        return null;
    }
    
    /**
     * 解析仓库URL
     */
    private RepoRef parseRepoUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new ScmAdapterException("unknown", "parseUrl", "Repository URL is null or empty");
        }
        
        String cleanUrl = url.trim();
        
        // Remove protocol
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            cleanUrl = cleanUrl.substring(cleanUrl.indexOf("://") + 3);
        }
        
        // Remove .git suffix
        if (cleanUrl.endsWith(".git")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 4);
        }
        
        // Parse different providers
        if (cleanUrl.startsWith("github.com/")) {
            return parseGitHubUrl(cleanUrl);
        } else if (cleanUrl.contains("gitlab.com/") || cleanUrl.contains("gitlab")) {
            return parseGitLabUrl(cleanUrl);
        } else if (cleanUrl.startsWith("bitbucket.org/")) {
            return parseBitbucketUrl(cleanUrl);
        } else {
            // Try to detect as Gitea or custom Git
            return parseCustomGitUrl(cleanUrl);
        }
    }
    
    private RepoRef parseGitHubUrl(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            return new RepoRef("github", parts[1], parts[2], "https://" + url);
        }
        throw new ScmAdapterException("github", "parseUrl", "Invalid GitHub URL format: " + url);
    }
    
    private RepoRef parseGitLabUrl(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            String provider = url.contains("gitlab.com") ? "gitlab" : "gitlab-self-hosted";
            return new RepoRef(provider, parts[1], parts[2], "https://" + url);
        }
        throw new ScmAdapterException("gitlab", "parseUrl", "Invalid GitLab URL format: " + url);
    }
    
    private RepoRef parseBitbucketUrl(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            return new RepoRef("bitbucket", parts[1], parts[2], "https://" + url);
        }
        throw new ScmAdapterException("bitbucket", "parseUrl", "Invalid Bitbucket URL format: " + url);
    }
    
    private RepoRef parseCustomGitUrl(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            // Default to gitea for custom Git servers
            return new RepoRef("gitea", parts[1], parts[2], "https://" + url);
        }
        throw new ScmAdapterException("custom", "parseUrl", "Invalid custom Git URL format: " + url);
    }
    
    // Header extraction methods
    
    private RepoRef extractRepoFromGitHubHeaders(Map<String, String> headers) {
        // For GitHub webhooks, we can create a basic RepoRef with provider info
        // The actual repo info will be extracted from the payload later
        logger.debug("Detected GitHub webhook from headers: {}", headers.get("X-GitHub-Event"));
        
        // Create a placeholder RepoRef for GitHub
        // The actual owner/name will be extracted from the webhook payload
        return new RepoRef("github", "unknown", "unknown", null);
    }
    
    private RepoRef extractRepoFromGitLabHeaders(Map<String, String> headers) {
        // For GitLab webhooks
        logger.debug("Detected GitLab webhook from headers: {}", headers.get("X-Gitlab-Event"));
        
        // Create a placeholder RepoRef for GitLab
        return new RepoRef("gitlab", "unknown", "unknown", null);
    }
    
    private RepoRef extractRepoFromBitbucketHeaders(Map<String, String> headers) {
        // For Bitbucket webhooks
        logger.debug("Detected Bitbucket webhook from headers: {}", headers.get("X-Event-Key"));
        
        // Create a placeholder RepoRef for Bitbucket
        return new RepoRef("bitbucket", "unknown", "unknown", null);
    }
    
    private RepoRef extractRepoFromGiteaHeaders(Map<String, String> headers) {
        // For Gitea webhooks (similar to GitHub)
        logger.debug("Detected Gitea webhook from headers: {}", headers.get("X-Gitea-Event"));
        
        // Create a placeholder RepoRef for Gitea
        return new RepoRef("gitea", "unknown", "unknown", null);
    }
}
