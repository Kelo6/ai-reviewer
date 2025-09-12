package com.ai.reviewer.integration;

import com.ai.reviewer.backend.BackendApplication;
import com.ai.reviewer.backend.api.dto.ApiResponse;
import com.ai.reviewer.backend.api.dto.ReviewRequest;
import com.ai.reviewer.backend.api.dto.ReviewRunResponse;
import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapter;
import com.ai.reviewer.backend.domain.adapter.scm.ScmAdapterRouter;
import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.reviewer.AiReviewer;
import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.FileStatus;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 端到端集成测试：验证完整的代码评审工作流程。
 * 
 * <p>测试包括：
 * <ul>
 *   <li>Flyway 数据库迁移</li>
 *   <li>使用 Mock 组件启动 app-backend</li>
 *   <li>通过 API 触发评审流程</li>
 *   <li>验证数据库记录和生成的报告文件</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {BackendApplication.class, EndToEndReviewIntegrationTest.TestConfig.class}
)
@Testcontainers
class EndToEndReviewIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ai_reviewer_test")
            .withUsername("test")
            .withPassword("test");

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ScmAdapterRouter scmAdapterRouter;

    @MockBean
    private ScmAdapter mockScmAdapter;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.profiles.active", () -> "integration-test");
        registry.add("ai-reviewer.reports.output-dir", () -> "./target/test-reports");
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        // 模拟 SCM 适配器行为
        when(scmAdapterRouter.getAdapter(any(RepoRef.class))).thenReturn(mockScmAdapter);
        when(mockScmAdapter.listDiff(any(RepoRef.class), any(PullRef.class))).thenReturn(createMockDiffHunks());
    }

    @Test
    void shouldRunCompleteEndToEndReviewWorkflow() throws Exception {
        // Given - 创建评审请求
        ReviewRequest.RepoInfo repoInfo = new ReviewRequest.RepoInfo("testorg", "test-repo");
        ReviewRequest.PullInfo pullInfo = new ReviewRequest.PullInfo("123", "Test PR", "feature-branch", "main");
        ReviewRequest request = new ReviewRequest(repoInfo, pullInfo, List.of("mock-analyzer", "mock-ai"));

        // When - 发起评审请求
        var createResponse = webTestClient
                .post()
                .uri("/api/review")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new TypeReference<ApiResponse<ReviewRunResponse>>() {})
                .returnResult()
                .getResponseBody();

        // Then - 验证创建响应
        assertThat(createResponse).isNotNull();
        assertThat(createResponse.ok()).isTrue();
        assertThat(createResponse.data()).isNotNull();
        assertThat(createResponse.data().runId()).isNotNull();

        String runId = createResponse.data().runId();

        // Step 1: 验证数据库中的 review_run 记录
        verifyReviewRunRecord(runId, repoInfo, pullInfo);

        // Step 2: 验证数据库中的 finding 记录
        verifyFindingRecords(runId);

        // Step 3: 验证数据库中的 score 记录
        verifyScoreRecords(runId);

        // Step 4: 验证数据库中的 artifact 记录
        verifyArtifactRecords(runId);

        // Step 5: 验证生成的报告文件
        verifyGeneratedReports(runId);

        // Step 6: 通过 GET API 获取评审结果并验证
        var getResponse = webTestClient
                .get()
                .uri("/api/runs/{runId}", runId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new TypeReference<ApiResponse<ReviewRunResponse>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(getResponse).isNotNull();
        assertThat(getResponse.ok()).isTrue();
        assertThat(getResponse.data()).isNotNull();

        // Step 7: 验证 scores 与数据库一致
        verifyScoresConsistency(runId, getResponse.data().scores());
    }

    @Test
    void shouldApplyFlywayMigrationsSuccessfully() {
        // 验证所有表都已创建
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class,
                mysql.getDatabaseName()
        );

        assertThat(tables).contains(
                "review_run",
                "finding", 
                "score",
                "artifact",
                "flyway_schema_history"
        );

        // 验证 flyway_schema_history 表中有迁移记录
        int migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class
        );
        assertThat(migrationCount).isGreaterThan(0);
    }

    @Test
    void shouldHandleEmptyDiffHunks() {
        // Given - 模拟空的 diff hunks
        when(mockScmAdapter.listDiff(any(RepoRef.class), any(PullRef.class)))
                .thenReturn(List.of());

        ReviewRequest.RepoInfo repoInfo = new ReviewRequest.RepoInfo("testorg", "empty-repo");
        ReviewRequest.PullInfo pullInfo = new ReviewRequest.PullInfo("456", "Empty PR", "fix-branch", "main");
        ReviewRequest request = new ReviewRequest(repoInfo, pullInfo, List.of("mock-analyzer"));

        // When - 发起评审请求
        var response = webTestClient
                .post()
                .uri("/api/review")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new TypeReference<ApiResponse<ReviewRunResponse>>() {})
                .returnResult()
                .getResponseBody();

        // Then - 验证空结果处理
        assertThat(response).isNotNull();
        assertThat(response.ok()).isTrue();
        assertThat(response.data().findings()).isEmpty();
        assertThat(response.data().scores().totalScore()).isEqualTo(100.0); // 完美分数
    }

    private void verifyReviewRunRecord(String runId, ReviewRequest.RepoInfo repo, ReviewRequest.PullInfo pull) {
        Map<String, Object> runRecord = jdbcTemplate.queryForMap(
                "SELECT * FROM review_run WHERE run_id = ?", runId
        );

        assertThat(runRecord).isNotEmpty();
        assertThat(runRecord.get("repo_owner")).isEqualTo(repo.owner());
        assertThat(runRecord.get("repo_name")).isEqualTo(repo.name());
        assertThat(runRecord.get("pull_number")).isEqualTo(pull.number());
        assertThat(runRecord.get("status")).isEqualTo("COMPLETED");
        assertThat(runRecord.get("files_changed")).isEqualTo(2); // 根据 mock data
    }

    private void verifyFindingRecords(String runId) {
        List<Map<String, Object>> findings = jdbcTemplate.queryForList(
                "SELECT * FROM finding WHERE run_id = ?", runId
        );

        assertThat(findings).isNotEmpty();
        assertThat(findings).hasSizeGreaterThanOrEqualTo(3); // mock analyzer + ai reviewer 的 findings

        // 验证至少有一个安全问题
        boolean hasSecurityFinding = findings.stream()
                .anyMatch(f -> "SECURITY".equals(f.get("dimension")));
        assertThat(hasSecurityFinding).isTrue();

        // 验证至少有一个 AI 发现的问题
        boolean hasAiFinding = findings.stream()
                .anyMatch(f -> f.get("sources").toString().contains("mock-ai-reviewer"));
        assertThat(hasAiFinding).isTrue();
    }

    private void verifyScoreRecords(String runId) {
        List<Map<String, Object>> scores = jdbcTemplate.queryForList(
                "SELECT * FROM score WHERE run_id = ?", runId
        );

        assertThat(scores).isNotEmpty();
        assertThat(scores).hasSizeGreaterThanOrEqualTo(5); // 5个维度

        // 验证所有维度都有分数
        List<String> dimensions = scores.stream()
                .map(s -> (String) s.get("dimension"))
                .toList();

        assertThat(dimensions).contains(
                "SECURITY", "QUALITY", "MAINTAINABILITY", "PERFORMANCE", "TEST_COVERAGE"
        );
    }

    private void verifyArtifactRecords(String runId) {
        Map<String, Object> artifact = jdbcTemplate.queryForMap(
                "SELECT * FROM artifact WHERE run_id = ?", runId
        );

        assertThat(artifact).isNotEmpty();
        assertThat(artifact.get("sarif_path")).isNotNull();
        assertThat(artifact.get("report_md_path")).isNotNull();
        assertThat(artifact.get("report_html_path")).isNotNull();
    }

    private void verifyGeneratedReports(String runId) {
        String reportsDir = "./target/test-reports/reports/" + runId;
        File reportsDirFile = new File(reportsDir);

        assertThat(reportsDirFile).exists().isDirectory();

        // 验证至少生成3种格式的报告
        File[] reportFiles = reportsDirFile.listFiles();
        assertThat(reportFiles).isNotNull();
        assertThat(reportFiles.length).isGreaterThanOrEqualTo(3);

        // 验证特定格式的文件存在
        List<String> fileNames = List.of(reportFiles).stream()
                .map(File::getName)
                .toList();

        boolean hasSarif = fileNames.stream().anyMatch(name -> name.endsWith(".sarif"));
        boolean hasMarkdown = fileNames.stream().anyMatch(name -> name.endsWith(".md"));
        boolean hasHtml = fileNames.stream().anyMatch(name -> name.endsWith(".html"));

        assertThat(hasSarif || hasMarkdown || hasHtml).isTrue();
    }

    private void verifyScoresConsistency(String runId, ReviewRunResponse.ScoresInfo apiScores) {
        List<Map<String, Object>> dbScores = jdbcTemplate.queryForList(
                "SELECT dimension, score FROM score WHERE run_id = ?", runId
        );

        Map<String, Object> dbScoreMap = dbScores.stream()
                .collect(java.util.stream.Collectors.toMap(
                        s -> (String) s.get("dimension"),
                        s -> s.get("score")
                ));

        // 验证 API 返回的分数与数据库一致
        for (Map.Entry<Dimension, Double> entry : apiScores.dimensions().entrySet()) {
            String dimensionName = entry.getKey().name();
            Double apiScore = entry.getValue();
            Object dbScore = dbScoreMap.get(dimensionName);

            assertThat(dbScore).isNotNull();
            assertThat(apiScore).isEqualTo(((Number) dbScore).doubleValue());
        }
    }

    private List<DiffHunk> createMockDiffHunks() {
        return List.of(
                new DiffHunk(
                        "src/main/java/TestClass.java",
                        FileStatus.MODIFIED,
                        "@@ -1,3 +1,7 @@\n public class TestClass {\n+    String query = \"SELECT * FROM users WHERE id = \" + userId;\n+    private DatabaseConnection conn = new DatabaseConnection();\n     // existing code\n }",
                        null
                ),
                new DiffHunk(
                        "src/main/java/UserService.java",
                        FileStatus.ADDED,
                        "@@ -0,0 +1,15 @@\n+public class UserService {\n+    public List<User> getAllUsers() {\n+        for (int i = 0; i < 1000000; i++) {\n+            // process large dataset\n+        }\n+    }\n+}",
                        null
                )
        );
    }

    /**
     * 测试配置：提供 Mock 实现。
     */
    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public StaticAnalyzer mockStaticAnalyzer() {
            return new MockStaticAnalyzerForIT();
        }

        @Bean
        @Primary
        public AiReviewer mockAiReviewer() {
            return new MockAiReviewerForIT();
        }
    }

    /**
     * 集成测试专用的 Mock StaticAnalyzer。
     */
    static class MockStaticAnalyzerForIT implements StaticAnalyzer {

        @Override
        public String getAnalyzerId() {
            return "it-mock-static-analyzer";
        }

        @Override
        public String getAnalyzerName() {
            return "IT Mock Static Analyzer";
        }

        @Override
        public String getVersion() {
            return "1.0.0-it";
        }

        @Override
        public boolean supportsFile(String fileName) {
            return fileName.endsWith(".java");
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<Finding> analyze(RepoRef repository, PullRef pullRequest, 
                                   List<DiffHunk> diffHunks, 
                                   com.ai.reviewer.backend.domain.config.AiReviewConfig config) {
            if (diffHunks.isEmpty()) {
                return List.of();
            }

            return List.of(
                    new Finding(
                            "IT-SEC-001",
                            diffHunks.get(0).file(),
                            5, 8,
                            Severity.MAJOR,
                            Dimension.SECURITY,
                            "SQL injection vulnerability detected in integration test",
                            "String concatenation in SQL query",
                            "Use parameterized queries to prevent SQL injection",
                            null,
                            List.of("it-mock-static-analyzer"),
                            0.9
                    ),
                    new Finding(
                            "IT-QUA-001",
                            diffHunks.get(0).file(),
                            12, 15,
                            Severity.MINOR,
                            Dimension.QUALITY,
                            "Code complexity issue detected in integration test",
                            "High cyclomatic complexity",
                            "Break down into smaller methods",
                            null,
                            List.of("it-mock-static-analyzer"),
                            0.75
                    )
            );
        }

        // 其他接口方法的简单实现
        @Override
        public java.util.concurrent.CompletableFuture<List<Finding>> analyzeAsync(CodeSegment segment, AnalysisContext context) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * 集成测试专用的 Mock AiReviewer。
     */
    static class MockAiReviewerForIT implements AiReviewer {

        @Override
        public String getReviewerId() {
            return "it-mock-ai-reviewer";
        }

        @Override
        public String getReviewerName() {
            return "IT Mock AI Reviewer";
        }

        @Override
        public String getModelVersion() {
            return "mock-gpt-4o-it";
        }

        @Override
        public boolean supportsLanguage(String language) {
            return "java".equals(language);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<Finding> review(RepoRef repository, PullRef pullRequest, 
                                  List<DiffHunk> diffHunks, 
                                  com.ai.reviewer.backend.domain.config.AiReviewConfig config) {
            if (diffHunks.isEmpty()) {
                return List.of();
            }

            return List.of(
                    new Finding(
                            "IT-AI-001",
                            diffHunks.get(0).file(),
                            3, 7,
                            Severity.MINOR,
                            Dimension.MAINTAINABILITY,
                            "Dependency injection suggestion from IT AI",
                            "Direct instantiation detected",
                            "Use dependency injection for better testability",
                            null,
                            List.of("it-mock-ai-reviewer"),
                            0.8
                    )
            );
        }

        // 其他接口方法的简单实现
        @Override
        public java.util.concurrent.CompletableFuture<List<Finding>> reviewAsync(
                StaticAnalyzer.CodeSegment segment, ReviewContext context) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<Finding>> reviewBatchAsync(
                List<StaticAnalyzer.CodeSegment> segments, ReviewContext context) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Finding> generateSummaryAsync(
                List<StaticAnalyzer.CodeSegment> allSegments, List<Finding> staticFindings, ReviewContext context) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Finding("IT-AI-SUMMARY", "SUMMARY", 1, 1, Severity.INFO, Dimension.QUALITY,
                            "IT AI Summary", "Summary from integration test", "No action needed",
                            null, List.of("it-mock-ai-reviewer"), 0.85)
            );
        }
    }
}
