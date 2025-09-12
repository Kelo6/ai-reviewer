package com.ai.reviewer.backend.domain.adapter.scm;

import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.model.PullRef;
import com.ai.reviewer.shared.model.RepoRef;

import java.util.List;
import java.util.Map;

/**
 * SCM (Source Code Management) adapter interface.
 * 
 * <p>Provides a unified abstraction layer for interacting with different
 * SCM providers (GitHub, GitLab, Bitbucket, etc.). Each implementation
 * handles the provider-specific API calls and data transformations.
 * 
 * <p>This interface supports the complete workflow of AI code reviews:
 * webhook verification, event parsing, pull request analysis, diff retrieval,
 * file content access, and feedback posting.
 */
public interface ScmAdapter {

    /**
     * Verify the webhook signature to ensure the request is authentic.
     * 
     * <p>Each SCM provider uses different signature algorithms and headers.
     * This method validates that the webhook request actually came from
     * the configured SCM provider and wasn't tampered with.
     *
     * @param headers HTTP headers from the webhook request
     * @param rawBody Raw request body bytes (needed for signature calculation)
     * @return true if the signature is valid, false otherwise
     */
    boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody);

    /**
     * Parse webhook event payload to extract relevant information.
     * 
     * <p>Converts the provider-specific webhook payload into a standardized
     * ParsedEvent format that can be processed uniformly by the review system.
     *
     * @param payload Raw webhook payload bytes
     * @param headers HTTP headers containing event metadata
     * @return parsed event with normalized data
     * @throws ScmAdapterException if the payload cannot be parsed
     */
    ParsedEvent parseEvent(byte[] payload, Map<String, String> headers);

    /**
     * Retrieve detailed pull request information.
     * 
     * <p>Fetches comprehensive pull request details including metadata,
     * status, and branch information that may not be available in
     * webhook payloads.
     *
     * @param repo repository reference
     * @param number pull request number (as string to support different formats)
     * @return detailed pull request information
     * @throws ScmAdapterException if the pull request cannot be retrieved
     */
    PullRef getPull(RepoRef repo, String number);

    /**
     * List all diff hunks for a pull request.
     * 
     * <p>Retrieves the complete diff information showing all changes
     * made in the pull request. This is the primary input for the
     * AI review analysis.
     *
     * @param repo repository reference
     * @param pull pull request reference
     * @return list of diff hunks showing all changes
     * @throws ScmAdapterException if diff information cannot be retrieved
     */
    List<DiffHunk> listDiff(RepoRef repo, PullRef pull);

    /**
     * Retrieve file content (blob) from a specific commit.
     * 
     * <p>Fetches the raw file content at a specific commit SHA.
     * Optionally supports retrieving only a specific line range
     * for performance optimization.
     *
     * @param repo repository reference
     * @param sha commit SHA to retrieve the file from
     * @param path file path relative to repository root
     * @param range optional line range to retrieve (null for entire file)
     * @return file content as string
     * @throws ScmAdapterException if the file cannot be retrieved
     */
    String getFileBlob(RepoRef repo, String sha, String path, Range range);

    /**
     * Create or update a check run/status for the pull request.
     * 
     * <p>Posts the overall review status to the SCM provider, which
     * typically appears as a check mark or status indicator on the
     * pull request page.
     *
     * @param repo repository reference
     * @param pull pull request reference
     * @param summary check summary with status and details
     * @throws ScmAdapterException if the check cannot be updated
     */
    void upsertCheck(RepoRef repo, PullRef pull, CheckSummary summary);

    /**
     * Post inline comments on specific lines of code.
     * 
     * <p>Creates review comments that appear directly on the diff view,
     * allowing reviewers to see AI feedback in context with the code changes.
     *
     * @param repo repository reference
     * @param pull pull request reference
     * @param comments list of inline comments to post
     * @throws ScmAdapterException if comments cannot be posted
     */
    void postInlineComments(RepoRef repo, PullRef pull, List<InlineComment> comments);

    /**
     * Create or update a summary comment on the pull request.
     * 
     * <p>Posts a comprehensive review summary as a pull request comment.
     * Uses an anchor key to identify and update existing summary comments
     * rather than creating duplicates.
     *
     * @param repo repository reference
     * @param pull pull request reference
     * @param anchorKey unique identifier to locate existing summary comments
     * @param body comment body (supports Markdown formatting)
     * @throws ScmAdapterException if the summary comment cannot be created/updated
     */
    void createOrUpdateSummaryComment(RepoRef repo, PullRef pull, String anchorKey, String body);

    /**
     * Get the provider identifier for this adapter.
     * 
     * @return provider name (e.g., "github", "gitlab", "bitbucket")
     */
    String getProvider();

    /**
     * Check if this adapter supports the given repository.
     * 
     * @param repo repository reference to check
     * @return true if this adapter can handle the repository
     */
    default boolean supports(RepoRef repo) {
        return getProvider().equalsIgnoreCase(repo.provider());
    }
}
