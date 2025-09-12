package com.ai.reviewer.shared.model;

/**
 * Pull request or merge request reference.
 * 
 * <p>Contains all essential information to identify and work with a specific
 * pull/merge request across different SCM providers. This record standardizes
 * the representation of code change requests regardless of the underlying
 * version control system.
 *
 * @param id Internal platform-specific ID of the pull/merge request
 * @param number Human-readable pull/merge request number (as string to support different formats)
 * @param title Title of the pull/merge request
 * @param sourceBranch Name of the source branch containing the changes
 * @param targetBranch Name of the target branch where changes will be merged
 * @param sha Latest commit SHA hash of the pull/merge request head
 * @param draft Whether this is a draft/WIP pull/merge request
 */
public record PullRef(
    String id,
    String number,
    String title,
    String sourceBranch,
    String targetBranch,
    String sha,
    boolean draft
) {}
