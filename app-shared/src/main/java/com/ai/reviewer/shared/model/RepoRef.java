package com.ai.reviewer.shared.model;

/**
 * Repository reference containing provider-specific information.
 * 
 * <p>Represents a source code repository in a version control system.
 * This record contains all necessary information to identify and access
 * a specific repository across different SCM providers.
 *
 * @param provider SCM provider identifier (e.g., "github", "gitlab", "bitbucket")
 * @param owner Repository owner or organization name
 * @param name Repository name
 * @param url Full URL to the repository
 */
public record RepoRef(
    String provider,
    String owner, 
    String name,
    String url
) {}
