package com.ai.reviewer.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 代码评审请求。
 */
public record ReviewRequest(
    @Valid @NotNull RepoInfo repo,
    @Valid @NotNull PullInfo pull,
    List<String> providers
) {
    
    /**
     * 仓库信息。
     */
    public record RepoInfo(
        @NotBlank String owner,
        @NotBlank String name
    ) {}
    
    /**
     * Pull Request信息。
     */
    public record PullInfo(
        @NotBlank String number,
        String title,
        String sourceBranch,
        String targetBranch
    ) {}
}
