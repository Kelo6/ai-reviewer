# 端到端集成测试说明

本文档说明如何运行 AI Reviewer 的端到端集成测试，验证完整的代码评审工作流程。

## 测试概述

### 🎯 测试目标

端到端测试验证以下完整流程：

1. **数据库迁移**: 应用 Flyway 迁移脚本
2. **组件启动**: 启动 app-backend，使用 Mock 的 ScmAdapter、StaticAnalyzer、AiReviewer
3. **API 调用**: 通过 POST /api/review 触发代码评审
4. **数据验证**: 验证数据库中的 review_run/finding/score/artifact 记录
5. **报告生成**: 验证 reports/{runId} 目录生成多种格式报告
6. **一致性检查**: 验证 GET /api/runs/{runId} 返回的 scores 与报告一致

### 📁 测试文件结构

```
integration-tests/
├── src/test/java/com/ai/reviewer/integration/
│   ├── EndToEndReviewIntegrationTest.java        # 完整API端到端测试
│   ├── MockComponentsIntegrationTest.java        # Mock组件功能测试
│   └── ReviewRunIntegrationTest.java            # 现有的基础集成测试
├── src/test/resources/
│   ├── application-integration-test.yml         # 测试配置
│   └── db/init-test-schema.sql                 # 测试数据库初始化
└── README-E2E-TESTING.md                       # 本说明文档
```

## 🧪 测试类说明

### 1. MockComponentsIntegrationTest

**专注验证**: Mock 组件的功能和报告生成

**主要测试**:
- ✅ Mock StaticAnalyzer 和 AiReviewer 的输出
- ✅ 配置驱动的置信度过滤
- ✅ 空 diff hunks 的处理
- ✅ 多格式报告文件生成（SARIF、Markdown、HTML、JSON）
- ✅ 数据库记录存储
- ✅ 端到端数据一致性

**运行方式**:
```bash
# 进入 integration-tests 目录
cd integration-tests

# 运行 Mock 组件测试
mvn test -Dtest=MockComponentsIntegrationTest
```

### 2. EndToEndReviewIntegrationTest

**专注验证**: 完整的 REST API 端到端流程

**主要测试**:
- ✅ 完整的 API 调用链路
- ✅ 数据库迁移验证
- ✅ HTTP 请求/响应处理
- ✅ 实际的 Spring Boot 应用启动
- ✅ 真实的数据库操作

**运行方式**:
```bash
# 运行完整端到端测试
mvn test -Dtest=EndToEndReviewIntegrationTest
```

## 🔧 Mock 组件功能

### MockStaticAnalyzer

模拟静态代码分析工具（如 Semgrep），返回预定义的安全和质量问题：

```java
// 生成的 Finding 示例
Finding securityFinding = new Finding(
    "MOCK-SEC-001",
    "src/main/java/TestClass.java",
    5, 8,
    Severity.MAJOR,
    Dimension.SECURITY,
    "SQL injection vulnerability detected",
    "String concatenation in SQL query",
    "Use parameterized queries to prevent SQL injection",
    null,
    List.of("mock-static-analyzer"),
    0.85
);
```

**特性**:
- 支持常见文件类型（.java, .js, .ts, .py, .go）
- 应用置信度阈值过滤
- 返回安全和质量维度的发现

### MockAiReviewer

模拟 AI 代码审查器（如 GPT-4），返回架构和设计建议：

```java
// 生成的 Finding 示例
Finding aiFinding = new Finding(
    "MOCK-AI-001",
    "src/main/java/TestClass.java",
    3, 7,
    Severity.MINOR,
    Dimension.MAINTAINABILITY,
    "Consider using dependency injection pattern",
    "Direct instantiation detected",
    "Use dependency injection for better testability",
    null,
    List.of("mock-ai-reviewer"),
    0.78
);
```

**特性**:
- 支持多种编程语言
- 应用置信度阈值过滤
- 返回可维护性和性能维度的建议

## 📊 验证的数据库记录

### review_run 表
```sql
SELECT run_id, repo_owner, repo_name, pull_number, status, 
       files_changed, total_score, created_at
FROM review_run WHERE run_id = ?
```

### finding 表
```sql
SELECT id, run_id, file, severity, dimension, title, 
       confidence, sources
FROM finding WHERE run_id = ?
```

### score 表
```sql
SELECT run_id, dimension, score, weight
FROM score WHERE run_id = ?
```

### artifact 表
```sql
SELECT run_id, sarif_path, report_md_path, 
       report_html_path, report_pdf_path
FROM artifact WHERE run_id = ?
```

## 📁 生成的报告文件

测试验证以下报告格式的生成：

### 1. SARIF 格式（report.sarif）
```json
{
  "version": "2.1.0",
  "runs": [{
    "tool": {
      "driver": {"name": "AI Reviewer Mock"}
    },
    "results": [{
      "ruleId": "MOCK-SEC-001",
      "message": {"text": "SQL injection vulnerability detected"},
      "level": "error"
    }]
  }]
}
```

### 2. Markdown 格式（report.md）
```markdown
# AI Code Review Report

## Summary
- Total Findings: 4
- Security Issues: 1
- Quality Issues: 1

## Findings
### Security
- **MOCK-SEC-001**: SQL injection vulnerability detected
```

### 3. HTML 格式（report.html）
完整的 HTML 报告，包含样式和交互功能

### 4. JSON 格式（report.json）
结构化的 JSON 数据，便于程序化处理

## 🚀 运行完整测试套件

### 预备条件
1. Docker 运行环境（用于 Testcontainers）
2. Maven 3.6+
3. Java 17+

### 运行所有集成测试
```bash
# 从项目根目录运行
mvn clean test -pl integration-tests

# 或者只运行集成测试
cd integration-tests
mvn clean test
```

### 运行特定测试
```bash
# 只运行 Mock 组件测试
mvn test -Dtest=MockComponentsIntegrationTest

# 只运行端到端 API 测试
mvn test -Dtest=EndToEndReviewIntegrationTest

# 运行特定测试方法
mvn test -Dtest=MockComponentsIntegrationTest#shouldRunCompleteWorkflowWithMockComponents
```

## 📋 测试结果验证

### 成功标准
- ✅ 所有测试用例通过
- ✅ 数据库记录正确创建
- ✅ 报告文件成功生成
- ✅ API 响应格式正确
- ✅ 数据一致性验证通过

### 输出示例
```
[INFO] Running MockComponentsIntegrationTest
[INFO] ✅ Mock components output verified
[INFO] ✅ Database records verified  
[INFO] ✅ Report files generated (4 formats)
[INFO] ✅ End-to-end consistency verified
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running EndToEndReviewIntegrationTest  
[INFO] ✅ Flyway migrations applied
[INFO] ✅ Spring Boot application started
[INFO] ✅ POST /api/review API successful
[INFO] ✅ GET /api/runs/{runId} API successful
[INFO] ✅ Database consistency verified
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

## 🐛 故障排除

### 常见问题

**1. Docker 连接失败**
```
Caused by: org.testcontainers.containers.ContainerLaunchException
```
**解决方案**: 确保 Docker 服务正在运行

**2. 端口冲突**
```
java.net.BindException: Address already in use
```
**解决方案**: 使用随机端口或停止占用端口的服务

**3. 依赖解析失败**
```
Could not resolve dependencies for project
```
**解决方案**: 从项目根目录运行 `mvn clean install`

### 调试技巧

**启用详细日志**:
```bash
mvn test -Dtest=MockComponentsIntegrationTest -Dlogging.level.com.ai.reviewer=DEBUG
```

**保留测试容器**:
```bash
mvn test -Dtestcontainers.reuse.enable=true
```

**查看生成的报告文件**:
```bash
ls -la integration-tests/target/test-reports/reports/
```

## 🎯 下一步

1. **扩展测试覆盖**: 添加更多边界条件和错误场景测试
2. **性能测试**: 添加大规模数据的性能验证
3. **真实集成**: 替换 Mock 组件为真实的 Semgrep/LLM 调用
4. **CI/CD 集成**: 将测试集成到持续集成管道

---

📝 **注意**: 这些测试使用 Mock 实现来验证工作流程的正确性。在生产环境中，需要替换为真实的静态分析工具和 AI 服务集成。
