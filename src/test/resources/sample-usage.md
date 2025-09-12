# ReportService 使用示例

## 基本用法

```java
@Service
public class ReviewController {
    
    private final ReportService reportService;
    
    public ReviewController(ReportService reportService) {
        this.reportService = reportService;
    }
    
    public ReviewRun.Artifacts generateReports(ReviewRun reviewRun) {
        try {
            return reportService.generateReports(reviewRun);
        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to generate reports", e);
        }
    }
}
```

## 生成的文件结构

```
reports/
└── {runId}/
    ├── report.json       # 单一事实源 - 完整的JSON报告
    ├── report.md         # Freemarker渲染的Markdown报告  
    ├── report.html       # Flexmark转换的HTML报告
    ├── report.pdf        # Flying Saucer生成的PDF报告
    └── findings.sarif    # SARIF 2.1.0格式的发现报告
```

## 报告内容

### report.json (单一事实源)
- ReviewRun 的完整信息
- Summary 摘要统计
- Scores 评分结果  
- Findings 发现的问题
- Artifacts 预留路径

### report.md
- 中文格式化的代码评审报告
- 包含评分概览、统计信息、问题详情
- 基于 Freemarker 模板渲染

### report.html
- 从 Markdown 转换的 HTML 版本
- 包含 CSS 样式和响应式布局
- 适合在浏览器中查看

### report.pdf
- 高质量的 PDF 报告
- 适合打印和分享
- 保持完整的格式和样式

### findings.sarif
- SARIF 2.1.0 标准格式
- 每个结果的 properties 包含 dimension 和 confidence
- 适合 CI/CD 集成和工具链

## 配置

在 `application.yml` 中配置输出目录：

```yaml
ai-reviewer:
  reports:
    output-dir: ${user.home}/ai-reviewer-reports
    create-dir-on-startup: true
    retention-days: 30
```
