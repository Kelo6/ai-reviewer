#!/bin/bash

# 简化的GitHub Webhook 测试脚本
SERVER_URL="http://localhost:8080"

# 检查服务
echo "🔧 GitHub Webhook 测试脚本"
echo "1. 检查服务状态..."
if ! curl -s "$SERVER_URL/health" > /dev/null; then
    echo "❌ 服务未运行"
    exit 1
fi
echo "✅ 服务运行正常"

# 发送完整的GitHub webhook
echo ""
echo "2. 发送GitHub Pull Request opened 事件..."
echo "   仓库: test-user/test-repo"
echo "   PR号: #1"

# 使用文件而不是内联JSON
cat > temp_payload.json <<'JSON_EOF'
{
  "action": "opened",
  "number": 1,
  "pull_request": {
    "id": 123456789,
    "number": 1,
    "state": "open",
    "title": "Enhanced security and validation improvements",
    "user": {
      "login": "test-user",
      "id": 12345,
      "type": "User"
    },
    "body": "This PR enhances security and adds comprehensive validation",
    "created_at": "2024-09-14T10:30:00Z",
    "updated_at": "2024-09-14T10:30:00Z",
    "head": {
      "label": "test-user:feature-security-enhancements",
      "ref": "feature-security-enhancements",
      "sha": "abc123def456789abcdef123456789abcdef1234",
      "repo": {
        "id": 987654321,
        "name": "test-repo",
        "full_name": "test-user/test-repo",
        "owner": {
          "login": "test-user",
          "id": 12345,
          "type": "User"
        },
        "html_url": "https://github.com/test-user/test-repo",
        "default_branch": "main"
      }
    },
    "base": {
      "label": "test-user:main", 
      "ref": "main",
      "sha": "def456abc789def456abc789def456abc789def4",
      "repo": {
        "id": 987654321,
        "name": "test-repo",
        "full_name": "test-user/test-repo",
        "owner": {
          "login": "test-user",
          "id": 12345,
          "type": "User"
        },
        "html_url": "https://github.com/test-user/test-repo",
        "default_branch": "main"
      }
    },
    "merged": false,
    "mergeable": true,
    "draft": false,
    "commits": 5,
    "additions": 87,
    "deletions": 10,
    "changed_files": 3,
    "diff_content": "Enhanced security and validation in UserService with comprehensive logging and OAuth2 configuration",
    "files": [
      {
        "filename": "src/main/java/com/example/UserService.java",
        "status": "modified",
        "additions": 45,
        "deletions": 8,
        "changes": 53,
        "patch": "--- a/src/main/java/com/example/UserService.java\n+++ b/src/main/java/com/example/UserService.java\n@@ -28,7 +28,35 @@ public class UserService {\n     private final UserRepository userRepository;\n     private final EmailService emailService;\n+    private static final Logger logger = LoggerFactory.getLogger(UserService.class);\n \n-    public User findUser(String id) {\n-        return userRepository.findById(id);\n+    public User findUser(String userId) {\n+        // Add comprehensive input validation\n+        if (userId == null || userId.trim().isEmpty()) {\n+            logger.error(\"Invalid user ID provided: {}\", userId);\n+            throw new IllegalArgumentException(\"User ID cannot be null or empty\");\n+        }\n+        \n+        logger.debug(\"Searching for user with ID: {}\", userId);\n+        User user = userRepository.findById(userId);\n+        return user;\n     }"
      },
      {
        "filename": "src/main/java/com/example/SecurityConfig.java",
        "status": "added",
        "additions": 25,
        "deletions": 0,
        "changes": 25,
        "patch": "--- /dev/null\n+++ b/src/main/java/com/example/SecurityConfig.java\n@@ -0,0 +1,25 @@\n+package com.example;\n+\n+@Configuration\n+@EnableWebSecurity\n+public class SecurityConfig {\n+    \n+    @Bean\n+    public PasswordEncoder passwordEncoder() {\n+        return new BCryptPasswordEncoder(12);\n+    }\n+    \n+    @Bean\n+    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {\n+        http\n+            .authorizeHttpRequests(authz -> authz\n+                .requestMatchers(\"/api/public/**\").permitAll()\n+                .requestMatchers(\"/admin/**\").hasRole(\"ADMIN\")\n+                .anyRequest().authenticated()\n+            );\n+        return http.build();\n+    }\n+}"
      }
    ]
  },
  "repository": {
    "id": 987654321,
    "name": "test-repo",
    "full_name": "test-user/test-repo",
    "owner": {
      "login": "test-user",
      "id": 12345,
      "type": "User"
    },
    "private": false,
    "html_url": "https://github.com/test-user/test-repo",
    "description": "Test repository for AI code review with enhanced security features"
  },
  "sender": {
    "login": "test-user",
    "id": 12345,
    "type": "User"
  }
}
JSON_EOF

# 发送webhook请求
response=$(curl -s -w "\nHTTP_STATUS_CODE:%{http_code}" \
    -X POST "$SERVER_URL/api/webhooks/generic?repoUrl=https://github.com/test-user/test-repo" \
    -H "Content-Type: application/json" \
    -H "X-GitHub-Event: pull_request" \
    -H "X-GitHub-Delivery: test-$(date +%s)" \
    -H "User-Agent: GitHub-Hookshot/test" \
    -d @temp_payload.json)

# 解析响应
http_code=$(echo "$response" | grep "HTTP_STATUS_CODE:" | cut -d: -f2)
response_body=$(echo "$response" | sed '/HTTP_STATUS_CODE:/d')

echo "   HTTP状态码: $http_code"
echo "   响应内容: $response_body"

# 清理临时文件
rm -f temp_payload.json

if [ "$http_code" = "200" ]; then
    echo "✅ Webhook发送成功"
    echo ""
    echo "🎉 **AI代码审查测试成功！**"
    echo ""
    echo "📊 **检查结果:**"
    echo "   仪表盘: $SERVER_URL/dashboard"
    echo ""
    echo "⏳ 等待几秒让AI处理完成..."
    sleep 5
    
    echo "📋 **最新记录:**"
    latest_run=$(powershell -Command "& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -proot -e 'USE ai_reviewer; SELECT run_id FROM review_run ORDER BY created_at DESC LIMIT 1;'" 2>/dev/null | tail -1)
    if [ ! -z "$latest_run" ]; then
        echo "   运行ID: $latest_run"
        echo "   详情页: $SERVER_URL/runs/$latest_run"
    fi
    
else
    echo "❌ Webhook发送失败"
    echo ""
    echo "请检查："
    echo "1. 服务是否正常运行"
    echo "2. JSON格式是否正确"
    echo "3. 网络连接是否正常"
fi
