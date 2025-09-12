package com.ai.reviewer.backend.api.controller;

import com.ai.reviewer.backend.domain.orchestrator.ReviewOrchestrator;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import com.ai.reviewer.backend.config.ReviewControllerTestConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * ReviewController MVC 测试。
 */
@WebMvcTest(controllers = ReviewController.class)
@ContextConfiguration(classes = ReviewControllerTestConfig.class)
@TestPropertySource(properties = {"server.servlet.context-path="})
@WithMockUser
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewOrchestrator reviewOrchestrator;

    @Test
    void testStartReview_Success() throws Exception {
        // Given
        String requestJson = """
            {
                "repo": {
                    "owner": "test-owner",
                    "name": "test-repo"
                },
                "pull": {
                    "number": "123",
                    "title": "Test PR",
                    "sourceBranch": "feature/test",
                    "targetBranch": "main"
                },
                "providers": ["gpt-4"]
            }
            """;

        ReviewRun mockReviewRun = createMockReviewRun();
        when(reviewOrchestrator.runReview(any(RepoRef.class), any(PullRef.class), anyList()))
            .thenReturn(mockReviewRun);

        // When & Then
        mockMvc.perform(post("/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(true)))
            .andExpect(jsonPath("$.data.runId", is("test-run-id")))
            .andExpect(jsonPath("$.data.repo.owner", is("test-owner")))
            .andExpect(jsonPath("$.data.repo.name", is("test-repo")))
            .andExpect(jsonPath("$.data.pull.number", is("123")))
            .andExpect(jsonPath("$.data.pull.title", is("Test PR")))
            .andExpect(jsonPath("$.data.scores.totalScore", is(85.5)))
            .andExpect(jsonPath("$.data.findings", hasSize(2)));
            // 成功响应不包含error字段
    }

    @Test
    void testStartReview_ValidationError() throws Exception {
        // Given - 缺少必填字段
        String requestJson = """
            {
                "repo": {
                    "name": "test-repo"
                },
                "pull": {
                    "number": "123"
                }
            }
            """;

        // When & Then
        mockMvc.perform(post("/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.message", containsString("参数验证失败")));
            // 错误响应不包含data字段
    }

    @Test
    void testStartReview_InternalError() throws Exception {
        // Given
        String requestJson = """
            {
                "repo": {
                    "owner": "test-owner",
                    "name": "test-repo"
                },
                "pull": {
                    "number": "123",
                    "title": "Test PR"
                }
            }
            """;

        when(reviewOrchestrator.runReview(any(RepoRef.class), any(PullRef.class), anyList()))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        mockMvc.perform(post("/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .with(csrf()))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("INTERNAL_ERROR")))
            .andExpect(jsonPath("$.error.message", containsString("服务内部错误")));
            // 错误响应不包含data字段
    }

    @Test
    void testGetReviewRun_Success() throws Exception {
        // Given
        String runId = "test-run-id";
        ReviewRun mockReviewRun = createMockReviewRun();
        when(reviewOrchestrator.getReviewRun(runId))
            .thenReturn(mockReviewRun);

        // When & Then
        mockMvc.perform(get("/runs/{runId}", runId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(true)))
            .andExpect(jsonPath("$.data.runId", is("test-run-id")))
            .andExpect(jsonPath("$.data.repo.owner", is("test-owner")))
            .andExpect(jsonPath("$.data.repo.name", is("test-repo")))
            .andExpect(jsonPath("$.data.stats.filesChanged", is(5)))
            .andExpect(jsonPath("$.data.stats.linesAdded", is(100)))
            .andExpect(jsonPath("$.data.stats.linesDeleted", is(50)))
            .andExpect(jsonPath("$.data.findings", hasSize(2)))
            .andExpect(jsonPath("$.data.findings[0].severity", is("MAJOR")))
            .andExpect(jsonPath("$.data.findings[0].dimension", is("SECURITY")));
            // 成功响应不包含error字段
    }

    @Test
    void testGetReviewRun_NotFound() throws Exception {
        // Given
        String runId = "non-existent-id";
        when(reviewOrchestrator.getReviewRun(runId))
            .thenReturn(null);

        // When & Then
        mockMvc.perform(get("/runs/{runId}", runId))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.error.code", is("RUN_NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", containsString("评审运行未找到")));
            // 错误响应不包含data字段
    }

    @Test
    void testStartReview_EmptyProviders() throws Exception {
        // Given - providers 为空数组
        String requestJson = """
            {
                "repo": {
                    "owner": "test-owner",
                    "name": "test-repo"
                },
                "pull": {
                    "number": "123",
                    "title": "Test PR"
                },
                "providers": []
            }
            """;

        ReviewRun mockReviewRun = createMockReviewRun();
        when(reviewOrchestrator.runReview(any(RepoRef.class), any(PullRef.class), anyList()))
            .thenReturn(mockReviewRun);

        // When & Then
        mockMvc.perform(post("/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok", is(true)))
            .andExpect(jsonPath("$.data.runId", is("test-run-id")));
    }

    @Test
    void testStartReview_MissingProviders() throws Exception {
        // Given - 不提供 providers 字段
        String requestJson = """
            {
                "repo": {
                    "owner": "test-owner",
                    "name": "test-repo"
                },
                "pull": {
                    "number": "123",
                    "title": "Test PR"
                }
            }
            """;

        ReviewRun mockReviewRun = createMockReviewRun();
        when(reviewOrchestrator.runReview(any(RepoRef.class), any(PullRef.class), anyList()))
            .thenReturn(mockReviewRun);

        // When & Then
        mockMvc.perform(post("/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok", is(true)))
            .andExpect(jsonPath("$.data.runId", is("test-run-id")));
    }

    /**
     * 创建模拟的 ReviewRun 对象。
     */
    private ReviewRun createMockReviewRun() {
        RepoRef repo = new RepoRef("github", "test-owner", "test-repo", "https://github.com/test-owner/test-repo");
        PullRef pull = new PullRef("123", "123", "Test PR", "feature/test", "main", "abc123", false);
        
        ReviewRun.Stats stats = new ReviewRun.Stats(5, 100, 50, 5000L, 0.25);
        
        List<Finding> findings = List.of(
            new Finding(
                "SECURITY-001",
                "src/main/java/TestFile.java",
                25, 28,
                Severity.MAJOR,
                Dimension.SECURITY,
                "SQL Injection Risk",
                "Direct SQL query construction detected",
                "Use parameterized queries",
                null,
                List.of("security-scanner"),
                0.85
            ),
            new Finding(
                "QUALITY-001",
                "src/main/java/TestFile.java",
                42, 45,
                Severity.MINOR,
                Dimension.QUALITY,
                "Code Style Issue",
                "Missing documentation",
                "Add Javadoc comments",
                null,
                List.of("checkstyle"),
                0.70
            )
        );
        
        Map<Dimension, Double> dimensionScores = Map.of(
            Dimension.SECURITY, 75.0,
            Dimension.QUALITY, 80.0,
            Dimension.MAINTAINABILITY, 90.0,
            Dimension.PERFORMANCE, 95.0,
            Dimension.TEST_COVERAGE, 85.0
        );
        
        Map<Dimension, Double> weights = Map.of(
            Dimension.SECURITY, 0.30,
            Dimension.QUALITY, 0.25,
            Dimension.MAINTAINABILITY, 0.20,
            Dimension.PERFORMANCE, 0.15,
            Dimension.TEST_COVERAGE, 0.10
        );
        
        Scores scores = new Scores(85.5, dimensionScores, weights);
        
        ReviewRun.Artifacts artifacts = new ReviewRun.Artifacts(
            "/reports/test-run-id/findings.sarif",
            "/reports/test-run-id/report.md",
            "/reports/test-run-id/report.html",
            "/reports/test-run-id/report.pdf"
        );
        
        return new ReviewRun(
            "test-run-id",
            repo,
            pull,
            Instant.parse("2024-01-15T10:30:00Z"),
            List.of("gpt-4"),
            stats,
            findings,
            scores,
            artifacts
        );
    }
}
