# AI Reviewer API 使用示例

## API 端点概览

### 1. 启动代码评审
**POST** `/api/review`

启动一次新的代码评审任务。

```json
// 请求示例
{
    "repo": {
        "owner": "ai-reviewer",
        "name": "demo-project"
    },
    "pull": {
        "number": "123",
        "title": "Add user authentication",
        "sourceBranch": "feature/auth",
        "targetBranch": "main"
    },
    "providers": ["gpt-4", "claude-3"]
}

// 成功响应
{
    "ok": true,
    "data": {
        "runId": "run-12345-abcdef",
        "repo": {
            "owner": "ai-reviewer",
            "name": "demo-project"
        },
        "pull": {
            "number": "123",
            "title": "Add user authentication"
        },
        "scores": {
            "totalScore": 85.2,
            "dimensions": {
                "SECURITY": 78.5,
                "QUALITY": 88.0,
                "MAINTAINABILITY": 92.1,
                "PERFORMANCE": 80.3,
                "TEST_COVERAGE": 75.8
            }
        },
        "findings": [
            {
                "id": "SEC-001",
                "file": "src/auth/UserController.java",
                "startLine": 25,
                "endLine": 28,
                "severity": "MAJOR",
                "dimension": "SECURITY",
                "title": "SQL Injection Risk",
                "evidence": "Direct SQL concatenation detected",
                "suggestion": "Use parameterized queries",
                "confidence": 0.92
            }
        ]
    }
}

// 错误响应
{
    "ok": false,
    "error": {
        "code": "VALIDATION_ERROR",
        "message": "参数验证失败: repo.owner 不能为空; pull.number 不能为空"
    }
}
```

### 2. 查询评审运行详情
**GET** `/api/runs/{runId}`

获取指定评审运行的完整详情。

```bash
# 请求示例
GET /api/runs/run-12345-abcdef

# 成功响应（与启动评审的响应格式相同）
{
    "ok": true,
    "data": {
        "runId": "run-12345-abcdef",
        "createdAt": "2024-01-15T10:30:00Z",
        "stats": {
            "filesChanged": 8,
            "linesAdded": 245,
            "linesDeleted": 67,
            "latencyMs": 12500,
            "tokenCostUsd": 0.48
        },
        "artifacts": {
            "sarifPath": "/reports/run-12345-abcdef/findings.sarif",
            "reportMdPath": "/reports/run-12345-abcdef/report.md",
            "reportHtmlPath": "/reports/run-12345-abcdef/report.html",
            "reportPdfPath": "/reports/run-12345-abcdef/report.pdf"
        }
    }
}

# 未找到响应
{
    "ok": false,
    "error": {
        "code": "RUN_NOT_FOUND",
        "message": "评审运行未找到: runId=non-existent-id"
    }
}
```

### 3. 下载报告文件
**GET** `/api/reports/{runId}.{ext}`

支持的文件格式：`md`、`html`、`pdf`、`json`、`sarif`

```bash
# 下载 Markdown 报告
GET /api/reports/run-12345-abcdef.md
Content-Type: text/markdown
Content-Disposition: attachment; filename="report-run-12345-abcdef.md"

# 下载 HTML 报告  
GET /api/reports/run-12345-abcdef.html
Content-Type: text/html

# 下载 PDF 报告
GET /api/reports/run-12345-abcdef.pdf
Content-Type: application/pdf

# 下载 JSON 报告（单一事实源）
GET /api/reports/run-12345-abcdef.json
Content-Type: application/json

# 下载 SARIF 报告
GET /api/reports/run-12345-abcdef.sarif
Content-Type: application/json
```

### 4. 健康检查
**GET** `/health`

检查系统各组件状态。

```json
// 健康响应
{
    "status": "UP",
    "timestamp": "2024-01-15T10:30:00Z",
    "components": {
        "database": {
            "status": "UP",
            "message": "Database connection is healthy",
            "details": {
                "driver": "MySQL Connector/J",
                "url": "jdbc:mysql://***:***@localhost:3306/ai_reviewer"
            }
        },
        "adapters": {
            "status": "UP",
            "message": "All adapters are available",
            "details": {
                "github": "Available",
                "gitlab": "Available"
            }
        },
        "memory": {
            "status": "UP",
            "message": "Memory usage is normal",
            "details": {
                "used": "512.5 MB",
                "total": "1.0 GB", 
                "max": "2.0 GB",
                "usage": "25.6%"
            }
        }
    }
}

// 异常响应（503 Service Unavailable）
{
    "status": "DOWN",
    "components": {
        "database": {
            "status": "DOWN",
            "message": "Database connection failed: Connection refused"
        }
    }
}
```

## 错误处理

所有API都遵循统一的错误响应格式：

```json
{
    "ok": false,
    "error": {
        "code": "错误代码",
        "message": "错误描述"
    }
}
```

### 常见错误代码

| 错误代码 | HTTP状态码 | 描述 |
|---------|-----------|------|
| `VALIDATION_ERROR` | 400 | 请求参数验证失败 |
| `UNAUTHORIZED` | 401 | 未授权访问 |
| `FORBIDDEN` | 403 | 禁止访问 |
| `RUN_NOT_FOUND` | 404 | 评审运行未找到 |
| `REPORT_NOT_FOUND` | 404 | 报告文件未找到 |
| `RATE_LIMIT_EXCEEDED` | 429 | 请求频率过高 |
| `INTERNAL_ERROR` | 500 | 服务内部错误 |
| `SERVICE_UNAVAILABLE` | 503 | 服务暂不可用 |

## 安全特性

### 敏感信息屏蔽
系统会自动屏蔽错误消息中的敏感信息：

- **API密钥**: `api_key=sk-1234...` → `api_key=***REDACTED***`
- **长提示词**: `prompt=长内容...` → `prompt=***CONTENT_REDACTED***`
- **数据库密码**: `jdbc:mysql://user:pass@...` → `jdbc:mysql://***:***@...`

### 请求验证
- 所有必填字段都会进行验证
- 支持嵌套对象验证
- 提供详细的验证错误信息

## 集成示例

### cURL 示例

```bash
# 启动评审
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{
    "repo": {"owner": "myorg", "name": "myrepo"},
    "pull": {"number": "123", "title": "Fix security issue"}
  }'

# 查询结果
curl http://localhost:8080/api/runs/run-12345-abcdef

# 下载报告
curl -O http://localhost:8080/api/reports/run-12345-abcdef.pdf

# 健康检查
curl http://localhost:8080/health
```

### Java 客户端示例

```java
@Service
public class ReviewClient {
    
    private final RestTemplate restTemplate;
    
    public ApiResponse<ReviewRunResponse> startReview(ReviewRequest request) {
        return restTemplate.postForObject("/api/review", request, 
            ApiResponse.class);
    }
    
    public ApiResponse<ReviewRunResponse> getReviewRun(String runId) {
        return restTemplate.getForObject("/api/runs/{runId}", 
            ApiResponse.class, runId);
    }
    
    public byte[] downloadReport(String runId, String format) {
        return restTemplate.getForObject("/api/reports/{runId}.{ext}",
            byte[].class, runId, format);
    }
}
```

## 性能说明

- **评审启动**: 通常需要 10-30 秒，取决于变更规模
- **报告生成**: 自动生成，包含在评审时间内  
- **文件下载**: 支持大文件流式传输
- **并发支持**: 支持多个评审任务并行处理

## 监控集成

健康检查端点可用于：
- **负载均衡器**: 健康检查配置
- **容器编排**: 健康探针配置  
- **监控系统**: 服务状态监控
- **告警系统**: 异常状态通知
