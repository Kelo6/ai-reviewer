# AI Reviewer 配置解析器

这个包实现了从仓库根目录的 `.ai-review.yml` 文件加载和解析配置的功能。

## 功能特性

### 🔧 配置文件格式
支持 YAML 格式的配置文件 (`.ai-review.yml`)：

```yaml
# SCM 提供商
provider: github|gitlab

# LLM 配置
llm:
  adapters: [gpt4o, claude35, local-qwen]  # 支持的LLM适配器
  budget_usd: 0.25                         # 预算限制（美元）

# 评分配置
scoring:
  # 维度权重（必须总和为 1.0）
  weights:
    SECURITY: 0.30
    QUALITY: 0.25
    MAINTAINABILITY: 0.20
    PERFORMANCE: 0.15
    TEST_COVERAGE: 0.10
  
  # 严重性惩罚分数（必须递增）
  severityPenalty:
    INFO: 1
    MINOR: 3
    MAJOR: 7
    CRITICAL: 12
  
  # 忽略置信度阈值
  ignoreConfidenceBelow: 0.3

# 报告配置
report:
  export:
    sarif: true   # SARIF 2.1.0 格式
    json: true    # JSON 格式（单一事实源）
    pdf: true     # PDF 格式
    html: true    # HTML 格式
```

### ✅ 配置验证
- **Bean Validation**: 使用 JSR-303 注解进行基础验证
- **自定义验证**: 业务逻辑验证（权重总和、严重性递增等）
- **缺省值**: 提供完整的默认配置
- **容错处理**: 配置文件不存在或无效时自动回退到默认配置

### 🏗️ 核心组件

#### 1. `AiReviewConfig` - 配置POJO
```java
public record AiReviewConfig(
    @NotNull String provider,
    @NotNull @Valid LlmConfig llm,
    @NotNull @Valid ScoringConfig scoring,
    @NotNull @Valid ReportConfig report
) {
    // 嵌套记录类：LlmConfig, ScoringConfig, ReportConfig
    // 提供默认配置和验证方法
}
```

#### 2. `ConfigService` - 配置服务
```java
@Service
public class ConfigService {
    // 从文件路径加载配置
    public AiReviewConfig loadConfig(String repoPath);
    
    // 从输入流加载配置
    public AiReviewConfig loadConfig(InputStream inputStream);
    
    // 保存配置到文件
    public void saveConfig(AiReviewConfig config, Path outputPath);
    
    // 合并配置
    public AiReviewConfig mergeConfig(AiReviewConfig default, AiReviewConfig override);
}
```

#### 3. `ConfigValidationException` - 配置验证异常
用于处理配置验证失败的情况。

## 使用示例

### 基础使用
```java
@Autowired
private ConfigService configService;

// 从仓库根目录加载配置
AiReviewConfig config = configService.loadConfig("/path/to/repo");

// 使用配置
if (config.scoring() != null) {
    Scores scores = scoringService.calculateScores(findings, linesChanged, config.scoring());
}

if (config.report() != null) {
    ReviewRun.Artifacts artifacts = reportService.generateReports(reviewRun, config.report());
}
```

### 集成到 ReviewOrchestrator
```java
// 简化的评审方法，自动加载配置
ReviewRun result = reviewOrchestrator.runReview(repoRef, pullRef, providers);

// 指定仓库路径的评审方法
ReviewRun result = reviewOrchestrator.runReviewWithConfig(repoRef, pullRef, repoPath, providers);
```

### 创建示例配置文件
```java
@Autowired
private ConfigService configService;

// 在指定路径创建示例配置
Path examplePath = Paths.get("/path/to/example-config.yml");
configService.createExampleConfig(examplePath);
```

## 配置规则与约束

### 🔒 必需配置
- **provider**: 必须是 "github" 或 "gitlab"
- **llm.adapters**: 至少指定一个LLM适配器
- **llm.budget_usd**: 必须是正数，不超过100美元
- **scoring.weights**: 所有维度权重，总和必须为1.0
- **scoring.severityPenalty**: 所有严重性级别的惩罚，必须递增
- **report.export**: 至少启用一种导出格式

### 📊 默认配置值
```java
AiReviewConfig defaultConfig = AiReviewConfig.getDefault();
// provider: "github"
// llm.adapters: ["gpt-4o", "claude-3.5-sonnet"]
// llm.budget_usd: 0.50
// scoring.ignoreConfidenceBelow: 0.3
// 所有报告格式: 启用
```

### ⚠️ 错误处理
- **文件不存在**: 返回默认配置，记录INFO日志
- **YAML解析错误**: 返回默认配置，记录ERROR日志  
- **验证失败**: 返回默认配置，记录ERROR日志
- **IO异常**: 返回默认配置，记录ERROR日志

## 扩展指南

### 添加新的配置选项
1. **扩展配置记录**:
   ```java
   public record AiReviewConfig(
       // 现有字段...
       @Valid NewSectionConfig newSection
   ) {}
   ```

2. **添加验证逻辑**:
   ```java
   public void validate() {
       // 现有验证...
       if (newSection != null) {
           newSection.validateNewSection();
       }
   }
   ```

3. **更新默认配置**:
   ```java
   public static AiReviewConfig getDefault() {
       return new AiReviewConfig(
           // 现有默认值...
           new NewSectionConfig(/* 默认值 */)
       );
   }
   ```

### 自定义配置加载
```java
@Component
public class CustomConfigLoader {
    
    @Autowired
    private ConfigService configService;
    
    public AiReviewConfig loadFromRemote(String remoteUrl) {
        // 从远程URL加载配置
        // 可以与本地配置合并
        AiReviewConfig remoteConfig = fetchRemoteConfig(remoteUrl);
        AiReviewConfig localConfig = configService.loadConfig(".");
        return configService.mergeConfig(localConfig, remoteConfig);
    }
}
```

## 测试

### 单元测试
- `ConfigServiceTest`: 配置服务测试
- `AiReviewConfigTest`: 配置POJO测试

### 集成测试
- `ConfigIntegrationTest`: 完整工作流测试

### 测试文件
- `src/test/resources/config/example-ai-review.yml`: 示例配置文件

## 最佳实践

### 📋 配置文件管理
1. **版本控制**: 将 `.ai-review.yml` 提交到代码仓库
2. **文档化**: 在项目README中说明配置选项
3. **团队共享**: 确保团队成员了解配置更改的影响
4. **环境差异**: 考虑不同环境的配置差异

### 🔐 安全考虑
1. **敏感信息**: 不要在配置文件中存储API密钥
2. **访问控制**: 限制配置文件的修改权限
3. **审计**: 记录配置更改历史

### ⚡ 性能优化
1. **缓存**: ConfigService会自动处理配置缓存
2. **验证**: 避免频繁验证同一配置
3. **回退**: 快速回退机制减少错误配置的影响

## 故障排除

### 常见问题

**Q: 配置文件存在但没有生效**
A: 检查文件路径、YAML语法和验证错误日志

**Q: 权重配置总是失败**
A: 确保所有维度都有权重且总和精确为1.0

**Q: 严重性惩罚验证失败**
A: 确保惩罚值按 INFO ≤ MINOR ≤ MAJOR ≤ CRITICAL 递增

**Q: 报告格式配置无效**
A: 至少启用一种导出格式

### 调试技巧
1. **启用DEBUG日志**: `logging.level.com.ai.reviewer.backend.domain.config=DEBUG`
2. **检查验证错误**: 查看 ConfigValidationException 详细信息
3. **使用默认配置**: 临时移除配置文件测试默认行为
