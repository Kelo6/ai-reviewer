# AI Reviewer Frontend

基于 Thymeleaf + HTMX 的前端 Web 应用，提供代码评审结果的可视化界面。

## 功能特性

### 🎯 核心功能
- **仪表板**: 分页显示历史评审运行记录，支持仓库和平台过滤
- **详情页面**: 展示评审总分、各维度雷达图、文件问题列表
- **报告预览**: 内嵌 HTML 报告预览，支持全屏查看
- **文件下载**: 支持 JSON、HTML、PDF、SARIF 格式报告下载
- **基础认证**: Spring Security 内存用户认证

### 📊 数据可视化
- **雷达图**: 使用 Chart.js 生成五维度评分雷达图
- **动态统计**: 实时显示问题统计、变更统计
- **进度条**: 各维度得分可视化
- **徽章标识**: 严重性等级色彩区分

### ⚡ 交互体验  
- **HTMX 增强**: 无刷新分页、动态加载
- **响应式设计**: 移动端友好的界面布局
- **加载状态**: 优雅的加载指示器
- **错误处理**: 友好的错误页面和提示

## 技术栈

- **后端框架**: Spring Boot 3.3.4
- **模板引擎**: Thymeleaf 3.x
- **前端增强**: HTMX 1.9.12
- **图表库**: Chart.js 4.4.0
- **安全框架**: Spring Security 6.x
- **HTTP客户端**: WebFlux (用于调用后端API)
- **样式**: 自定义 CSS（现代设计系统）

## 目录结构

```
app-frontend/
├── src/main/
│   ├── java/com/ai/reviewer/frontend/
│   │   ├── FrontendApplication.java          # 主启动类
│   │   ├── config/
│   │   │   ├── SecurityConfig.java           # Spring Security配置
│   │   │   └── WebConfig.java                # WebClient配置
│   │   ├── controller/                       # Web控制器
│   │   │   ├── AuthController.java           # 认证控制器
│   │   │   ├── DashboardController.java      # 仪表板控制器  
│   │   │   ├── ReportController.java         # 报告控制器
│   │   │   └── RunController.java            # 运行详情控制器
│   │   ├── dto/                             # 数据传输对象
│   │   │   ├── ApiResponseDto.java           # API响应DTO
│   │   │   └── ReviewRunDto.java             # 评审运行DTO
│   │   └── service/                         # 服务层
│   │       ├── ReportService.java            # 报告服务
│   │       └── ReviewService.java            # 评审服务
│   └── resources/
│       ├── static/                          # 静态资源
│       │   ├── css/main.css                 # 主样式文件
│       │   └── js/
│       │       ├── chart.min.js             # Chart.js库
│       │       └── charts.js                # 图表工具类
│       ├── templates/                       # Thymeleaf模板
│       │   ├── dashboard.html               # 仪表板页面
│       │   ├── login.html                   # 登录页面
│       │   ├── layout.html                  # 基础布局
│       │   ├── run-details.html             # 运行详情页面
│       │   ├── report-preview.html          # 报告预览页面
│       │   ├── fragments/                   # 模板片段
│       │   │   ├── findings.html            # 问题列表片段
│       │   │   └── runs-table.html          # 运行记录表格片段
│       │   └── error/                       # 错误页面
│       │       ├── 404.html
│       │       └── 500.html
│       └── application.yml                  # 应用配置
└── src/test/                               # 测试代码
    └── java/com/ai/reviewer/frontend/
        └── controller/
            └── DashboardControllerTest.java
```

## 配置说明

### 应用配置 (application.yml)

```yaml
app:
  backend:
    base-url: http://localhost:8080    # 后端API基础URL
    timeout: 30s                       # 请求超时时间
  
  security:
    enabled: true                      # 是否启用安全认证
    default-user: admin                # 默认管理员用户名
    default-password: password         # 默认管理员密码

server:
  port: 8081                          # 前端服务端口

spring:
  thymeleaf:
    cache: false                      # 开发环境禁用模板缓存
```

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|-------|------|
| `BACKEND_BASE_URL` | `http://localhost:8080` | 后端API地址 |
| `SECURITY_ENABLED` | `true` | 是否启用认证 |
| `DEFAULT_USER` | `admin` | 默认用户名 |
| `DEFAULT_PASSWORD` | `password` | 默认密码 |

## 快速开始

### 1. 启动后端服务
确保 `app-backend` 服务正在 8080 端口运行。

### 2. 启动前端服务
```bash
cd app-frontend
mvn spring-boot:run
```

### 3. 访问应用
- 应用地址: http://localhost:8081
- 默认账户: admin / password 或 user / user123

## 页面说明

### 🏠 仪表板 (`/dashboard`)
- 分页显示历史评审运行记录
- 支持按仓库名称和平台筛选
- 实时更新运行状态和评分
- HTMX 无刷新分页加载

### 📊 运行详情 (`/runs/{runId}`)
- 基本信息：仓库、PR、分支、处理时间等
- 评分概览：总分和五维度雷达图
- 统计信息：文件变更、代码行数、问题数量
- 问题详情：按严重性分类的详细问题列表
- 下载选项：多格式报告文件

### 📄 报告预览 (`/reports/{runId}`)
- HTML 报告内嵌预览
- 支持全屏查看模式
- 代码块复制功能
- 平滑滚动导航

### 🔐 登录页面 (`/login`)
- 简洁的登录界面
- 友好的错误提示
- 默认账户说明

## 技术特性

### HTMX 集成
- 无刷新分页加载
- 动态内容更新
- 优雅的加载状态
- 错误处理机制

### Chart.js 图表
- 五维度雷达图
- 响应式图表设计
- 交互式图例
- 自定义颜色主题

### Spring Security
- 内存用户管理
- 表单登录认证
- 会话管理
- CSRF 保护

### 响应式设计
- 移动端优化
- 灵活的网格布局
- 触摸友好的交互
- 现代化的设计语言

## 开发指南

### 添加新页面
1. 在 `controller/` 创建控制器
2. 在 `templates/` 创建模板
3. 在 `service/` 添加服务逻辑（如需要）
4. 更新导航和路由

### 自定义样式
- 修改 `static/css/main.css`
- 使用 CSS 变量进行主题定制
- 遵循现有的设计系统

### 添加图表
- 使用 `window.ReviewCharts` 工具类
- 支持雷达图和柱状图
- 可扩展其他 Chart.js 图表类型

### HTMX 集成
- 使用 `hx-*` 属性进行增强
- 利用模板片段进行部分更新
- 配置加载指示器

## 安全考虑

### 默认安全配置
- 启用 CSRF 保护
- 会话管理
- 静态资源免认证
- 下载请求 CSRF 豁免

### 生产部署建议
1. 修改默认密码
2. 使用 HTTPS
3. 配置会话超时
4. 启用安全头部
5. 定期更新依赖

## 故障排除

### 常见问题
1. **后端连接失败**: 检查 `BACKEND_BASE_URL` 配置
2. **图表不显示**: 确认 Chart.js 文件加载正常
3. **HTMX 不工作**: 检查浏览器控制台错误
4. **认证失败**: 验证用户名密码配置

### 日志配置
```yaml
logging:
  level:
    com.ai.reviewer: DEBUG
    org.springframework.security: DEBUG
```

## 扩展指南

### 集成外部认证
- OAuth2 集成（GitHub、GitLab）
- LDAP 认证
- 自定义用户存储

### 功能增强
- 实时通知
- 更多图表类型
- 高级筛选选项
- 导出功能

### 性能优化
- 启用模板缓存
- 压缩静态资源
- 配置浏览器缓存
- CDN 加速

## 许可证

本项目采用 MIT 许可证。
