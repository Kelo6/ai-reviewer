/**
 * SCM (Source Code Management) adapter package.
 * 
 * <p>This package provides a unified abstraction layer for interacting with
 * different SCM providers such as GitHub, GitLab, Bitbucket, and others.
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>ScmAdapter Interface</h3>
 * <p>The main interface that defines the contract for SCM operations:
 * <ul>
 *   <li>Webhook signature verification</li>
 *   <li>Event parsing and normalization</li>
 *   <li>Pull request and diff retrieval</li>
 *   <li>File content access</li>
 *   <li>Status checks and comment posting</li>
 * </ul>
 * 
 * <h3>ScmAdapterRouter</h3>
 * <p>Router component that selects the appropriate adapter based on the
 * repository provider. Supports automatic adapter discovery through Spring's
 * dependency injection.
 * 
 * <h3>DTO Records</h3>
 * <ul>
 *   <li>{@link com.ai.reviewer.backend.domain.adapter.scm.ParsedEvent} - Normalized webhook events</li>
 *   <li>{@link com.ai.reviewer.backend.domain.adapter.scm.CheckSummary} - Check run status information</li>
 *   <li>{@link com.ai.reviewer.backend.domain.adapter.scm.InlineComment} - Code review comments</li>
 *   <li>{@link com.ai.reviewer.backend.domain.adapter.scm.Range} - Line range specifications</li>
 * </ul>
 * 
 * <h2>Implementation Guide</h2>
 * 
 * <p>To create a new SCM adapter:
 * 
 * <pre>{@code
 * @Component
 * public class GitHubAdapter implements ScmAdapter {
 *     
 *     @Override
 *     public String getProvider() {
 *         return "github";
 *     }
 *     
 *     @Override
 *     public boolean verifyWebhookSignature(Map<String, String> headers, byte[] rawBody) {
 *         // GitHub-specific signature verification
 *         return GitHubSignatureValidator.verify(headers, rawBody);
 *     }
 *     
 *     // ... implement other methods
 * }
 * }</pre>
 * 
 * <h2>Error Handling</h2>
 * 
 * <p>All adapter operations should throw {@link com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterException}
 * for consistent error handling across providers.
 * 
 * <h2>Configuration</h2>
 * 
 * <p>Adapters typically require provider-specific configuration such as:
 * <ul>
 *   <li>API tokens or authentication credentials</li>
 *   <li>Webhook secrets for signature verification</li>
 *   <li>API endpoint URLs (for self-hosted instances)</li>
 *   <li>Rate limiting and retry policies</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package com.ai.reviewer.backend.domain.adapter.scm;
