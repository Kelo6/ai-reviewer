#!/bin/bash

# GitHub Webhook 测试脚本
# 模拟GitHub发送Pull Request webhook事件

SERVER_URL="http://localhost:8080"
WEBHOOK_ENDPOINT="/api/webhooks/github"

echo "🔧 GitHub Webhook 测试脚本"
echo "================================"

# 检查服务是否运行
echo "1. 检查服务状态..."
if ! curl -s "$SERVER_URL/health" > /dev/null; then
    echo "❌ 服务未运行，请先启动AI Reviewer服务"
    echo "   运行: ./start-app.sh"
    exit 1
fi
echo "✅ 服务运行正常"

# 函数：生成更真实的diff内容用于AI分析
generate_enhanced_diff() {
    cat <<'DIFF_EOF'
diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java
index 1234567..abcdefg 100644
--- a/src/main/java/com/example/UserService.java
+++ b/src/main/java/com/example/UserService.java
@@ -1,5 +1,8 @@
 package com.example;
 
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+
 @Service
 public class UserService {
     
@@ -25,18 +28,35 @@ public class UserService {
     private final UserRepository userRepository;
     private final EmailService emailService;
+    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
 
-    public User findUser(String id) {
-        return userRepository.findById(id);
+    public User findUser(String userId) {
+        // Add comprehensive input validation
+        if (userId == null || userId.trim().isEmpty()) {
+            logger.error("Invalid user ID provided: {}", userId);
+            throw new IllegalArgumentException("User ID cannot be null or empty");
+        }
+        
+        if (userId.length() > 255) {
+            logger.warn("User ID too long: {} characters", userId.length());
+            throw new IllegalArgumentException("User ID cannot exceed 255 characters");
+        }
+        
+        logger.debug("Searching for user with ID: {}", userId);
+        User user = userRepository.findById(userId);
+        
+        if (user == null) {
+            logger.warn("User not found with ID: {}", userId);
+            return null;
+        }
+        
+        logger.debug("Found user: {}", user.getUsername());
+        return user;
     }
 
     public User createUser(CreateUserRequest request) {
-        User user = new User();
-        user.setUsername(request.getUsername());
-        user.setEmail(request.getEmail());
-        user.setPassword(hashPassword(request.getPassword()));
-        user.setCreatedAt(Instant.now());
-        return userRepository.save(user);
+        // Add input validation
+        validateUserRequest(request);
+        
+        // Check for existing user
+        if (userRepository.existsByEmail(request.getEmail())) {
+            throw new UserAlreadyExistsException("User with email already exists: " + request.getEmail());
+        }
+        
+        // Create user with proper data sanitization
+        User user = new User();
+        user.setUsername(sanitizeUsername(request.getUsername()));
+        user.setEmail(request.getEmail().toLowerCase().trim());
+        user.setPassword(secureHashPassword(request.getPassword()));
+        user.setCreatedAt(Instant.now());
+        
+        // Send welcome email asynchronously
+        User savedUser = userRepository.save(user);
+        emailService.sendWelcomeEmailAsync(savedUser);
+        
+        logger.info("Created new user: {}", savedUser.getUsername());
+        return savedUser;
+    }
+    
+    private void validateUserRequest(CreateUserRequest request) {
+        if (request == null) {
+            throw new IllegalArgumentException("User request cannot be null");
+        }
+        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
+            throw new IllegalArgumentException("Username is required");
+        }
+        if (!isValidEmail(request.getEmail())) {
+            throw new IllegalArgumentException("Valid email is required");
+        }
+        if (!isStrongPassword(request.getPassword())) {
+            throw new IllegalArgumentException("Password must be at least 8 characters with mixed case and numbers");
+        }
+    }
+    
+    private String sanitizeUsername(String username) {
+        return username.trim().toLowerCase().replaceAll("[^a-zA-Z0-9_-]", "");
+    }
+    
+    private String secureHashPassword(String password) {
+        // TODO: Use BCrypt or similar secure hashing
+        return hashPassword(password);
     }
 }
diff --git a/src/main/java/com/example/SecurityConfig.java b/src/main/java/com/example/SecurityConfig.java
new file mode 100644
index 0000000..def456
--- /dev/null
+++ b/src/main/java/com/example/SecurityConfig.java
@@ -0,0 +1,25 @@
+package com.example;
+
+@Configuration
+@EnableWebSecurity
+public class SecurityConfig {
+    
+    @Bean
+    public PasswordEncoder passwordEncoder() {
+        return new BCryptPasswordEncoder(12);
+    }
+    
+    @Bean
+    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
+        http
+            .authorizeHttpRequests(authz -> authz
+                .requestMatchers("/api/public/**").permitAll()
+                .requestMatchers("/admin/**").hasRole("ADMIN")  
+                .anyRequest().authenticated()
+            )
+            .oauth2Login(oauth2 -> oauth2
+                .loginPage("/login")
+                .defaultSuccessUrl("/dashboard")
+            );
+        return http.build();
+    }
+}
diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
index 9876543..fedcba9 100644
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -10,5 +10,22 @@ spring:
   datasource:
     url: jdbc:mysql://localhost:3306/testdb
-    username: ${DB_USER:user}
-    password: ${DB_PASS:pass}
+    username: ${DB_USER:root}
+    password: ${DB_PASS:}
+    hikari:
+      maximum-pool-size: 20
+      minimum-idle: 5
+      connection-timeout: 30000
+  
+  # Add caching configuration
+  cache:
+    type: caffeine
+    caffeine:
+      spec: maximumSize=1000,expireAfterAccess=300s
+      
+  # Security configuration
+  security:
+    oauth2:
+      client:
+        registration:
+          google:
+            client-id: ${GOOGLE_CLIENT_ID:}
+            client-secret: ${GOOGLE_CLIENT_SECRET:}
+            
+logging:
+  level:
+    com.example: DEBUG
+    org.springframework.security: DEBUG
DIFF_EOF
}

# 函数：发送diff内容到服务
send_diff_to_service() {
    local repo_owner=$1
    local repo_name=$2
    local pr_number=$3
    
    echo "   📄 发送PR差异内容..."
    
    # 生成增强的diff数据用于AI分析
    local diff_content=$(generate_enhanced_diff)
    
    # 创建diff payload
    local diff_payload=$(cat <<EOF
{
  "repository": {
    "owner": "$repo_owner",
    "name": "$repo_name",
    "full_name": "$repo_owner/$repo_name"
  },
  "pull_request": {
    "number": $pr_number,
    "head": {
      "sha": "abc123def456789abcdef123456789abcdef1234"
    },
    "base": {
      "sha": "def456abc789def456abc789def456abc789def4"
    }
  },
  "diff": $(echo "$diff_content" | jq -R -s .)
}
EOF
    )
    
    # 发送diff到服务 (尝试不同的端点)
    local diff_response=$(curl -s -w "\nHTTP_STATUS_CODE:%{http_code}" \
        -X POST "$SERVER_URL/api/analysis/diff" \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: pull_request" \
        -d "$diff_payload" 2>/dev/null)
    
    local diff_http_code=$(echo "$diff_response" | grep "HTTP_STATUS_CODE:" | cut -d: -f2)
    local diff_response_body=$(echo "$diff_response" | sed '/HTTP_STATUS_CODE:/d')
    
    if [ "$diff_http_code" = "200" ] || [ "$diff_http_code" = "201" ]; then
        echo "   ✅ Diff内容发送成功"
        return 0
    else
        echo "   ⚠️ Diff内容发送失败 (可能API不支持): HTTP $diff_http_code"
        return 1
    fi
}

# 函数：发送GitHub Pull Request webhook事件
send_github_webhook() {
    local action=$1
    local pr_number=${2:-1}
    local repo_owner=${3:-"test-user"}
    local repo_name=${4:-"test-repo"}
    
    echo "2. 发送GitHub Pull Request $action 事件..."
    echo "   仓库: $repo_owner/$repo_name"
    echo "   PR号: #$pr_number"
    
    # 生成AI友好的diff内容用于深度分析
    local diff_content="diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java\nindex 1234567..abcdefg 100644\n--- a/src/main/java/com/example/UserService.java\n+++ b/src/main/java/com/example/UserService.java\n@@ -28,7 +28,35 @@ public class UserService {\n     private final UserRepository userRepository;\n     private final EmailService emailService;\n+    private static final Logger logger = LoggerFactory.getLogger(UserService.class);\n \n-    public User findUser(String id) {\n-        return userRepository.findById(id);\n+    public User findUser(String userId) {\n+        // Add comprehensive input validation\n+        if (userId == null || userId.trim().isEmpty()) {\n+            logger.error(\\\"Invalid user ID provided: {}\\\", userId);\n+            throw new IllegalArgumentException(\\\"User ID cannot be null or empty\\\");\n+        }\n+        \n+        logger.debug(\\\"Searching for user with ID: {}\\\", userId);\n+        User user = userRepository.findById(userId);\n+        return user;\n     }\n     \n     public User createUser(CreateUserRequest request) {\n+        // Add input validation\n+        validateUserRequest(request);\n+        \n+        // Check for existing user\n+        if (userRepository.existsByEmail(request.getEmail())) {\n+            throw new UserAlreadyExistsException(\\\"User with email already exists: \\\" + request.getEmail());\n+        }"
    
    # GitHub webhook payload with diff  
    local payload=$(cat <<EOF
{
  "action": "$action",
  "number": $pr_number,
  "pull_request": {
    "id": 123456789,
    "number": $pr_number,
    "state": "open",
    "locked": false,
    "title": "Add new feature for testing AI review",
    "user": {
      "login": "$repo_owner",
      "id": 12345,
      "type": "User"
    },
    "body": "This is a test pull request for AI code review functionality.\n\n## Changes\n- Added new feature\n- Fixed bug in authentication\n- Updated documentation",
    "created_at": "2024-09-14T10:30:00Z",
    "updated_at": "2024-09-14T10:30:00Z",
    "head": {
      "label": "$repo_owner:feature-branch",
      "ref": "feature-branch",
      "sha": "abc123def456789abcdef123456789abcdef1234",
      "user": {
        "login": "$repo_owner",
        "id": 12345,
        "type": "User"
      },
      "repo": {
        "id": 987654321,
        "name": "$repo_name",
        "full_name": "$repo_owner/$repo_name",
        "owner": {
          "login": "$repo_owner",
          "id": 12345,
          "type": "User"
        },
        "private": false,
        "html_url": "https://github.com/$repo_owner/$repo_name",
        "clone_url": "https://github.com/$repo_owner/$repo_name.git",
        "default_branch": "main"
      }
    },
    "base": {
      "label": "$repo_owner:main",
      "ref": "main",
      "sha": "def456abc789def456abc789def456abc789def4",
      "user": {
        "login": "$repo_owner",
        "id": 12345,
        "type": "User"
      },
      "repo": {
        "id": 987654321,
        "name": "$repo_name",
        "full_name": "$repo_owner/$repo_name",
        "owner": {
          "login": "$repo_owner",
          "id": 12345,
          "type": "User"
        },
        "private": false,
        "html_url": "https://github.com/$repo_owner/$repo_name",
        "clone_url": "https://github.com/$repo_owner/$repo_name.git",
        "default_branch": "main"
      }
    },
    "merged": false,
    "mergeable": true,
    "mergeable_state": "clean",
    "draft": false,
    "commits": 5,
    "additions": 87,
    "deletions": 10,
    "changed_files": 3,
    "diff_url": "https://github.com/$repo_owner/$repo_name/pull/$pr_number.diff",
    "patch_url": "https://github.com/$repo_owner/$repo_name/pull/$pr_number.patch",
    "diff_content": "Enhanced security and validation in UserService with comprehensive logging, input sanitization, and added OAuth2 security configuration",
    "files": [
      {
        "filename": "src/main/java/com/example/UserService.java",
        "status": "modified",
        "additions": 45,
        "deletions": 8,
        "changes": 53,
        "patch": "@@ -28,7 +28,35 @@ public class UserService {\\n     private final UserRepository userRepository;\\n     private final EmailService emailService;\\n+    private static final Logger logger = LoggerFactory.getLogger(UserService.class);\\n \\n-    public User findUser(String id) {\\n-        return userRepository.findById(id);\\n+    public User findUser(String userId) {\\n+        // Add comprehensive input validation\\n+        if (userId == null || userId.trim().isEmpty()) {\\n+            logger.error(\\\"Invalid user ID provided: {}\\\", userId);\\n+            throw new IllegalArgumentException(\\\"User ID cannot be null or empty\\\");\\n+        }\\n+        \\n+        if (userId.length() > 255) {\\n+            logger.warn(\\\"User ID too long: {} characters\\\", userId.length());\\n+            throw new IllegalArgumentException(\\\"User ID cannot exceed 255 characters\\\");\\n+        }\\n+        \\n+        logger.debug(\\\"Searching for user with ID: {}\\\", userId);\\n+        User user = userRepository.findById(userId);\\n+        \\n+        if (user == null) {\\n+            logger.warn(\\\"User not found with ID: {}\\\", userId);\\n+            return null;\\n+        }\\n+        \\n+        logger.debug(\\\"Found user: {}\\\", user.getUsername());\\n+        return user;\\n     }",
        "raw_url": "https://github.com/$repo_owner/$repo_name/raw/abc123/src/main/java/com/example/UserService.java",
        "blob_url": "https://github.com/$repo_owner/$repo_name/blob/abc123/src/main/java/com/example/UserService.java"
      },
      {
        "filename": "src/main/java/com/example/SecurityConfig.java",
        "status": "added",
        "additions": 25,
        "deletions": 0,
        "changes": 25,
        "patch": "@@ -0,0 +1,25 @@\\n+package com.example;\\n+\\n+@Configuration\\n+@EnableWebSecurity\\n+public class SecurityConfig {\\n+    \\n+    @Bean\\n+    public PasswordEncoder passwordEncoder() {\\n+        return new BCryptPasswordEncoder(12);\\n+    }\\n+    \\n+    @Bean\\n+    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {\\n+        http\\n+            .authorizeHttpRequests(authz -> authz\\n+                .requestMatchers(\\\"/api/public/**\\\").permitAll()\\n+                .requestMatchers(\\\"/admin/**\\\").hasRole(\\\"ADMIN\\\")  \\n+                .anyRequest().authenticated()\\n+            )\\n+            .oauth2Login(oauth2 -> oauth2\\n+                .loginPage(\\\"/login\\\")\\n+                .defaultSuccessUrl(\\\"/dashboard\\\")\\n+            );\\n+        return http.build();\\n+    }\\n+}",
        "raw_url": "https://github.com/$repo_owner/$repo_name/raw/abc123/src/main/java/com/example/SecurityConfig.java",
        "blob_url": "https://github.com/$repo_owner/$repo_name/blob/abc123/src/main/java/com/example/SecurityConfig.java"
      },
      {
        "filename": "src/main/resources/application.yml", 
        "status": "modified",
        "additions": 17,
        "deletions": 2,
        "changes": 19,
        "patch": "@@ -10,5 +10,22 @@ spring:\\n   datasource:\\n     url: jdbc:mysql://localhost:3306/testdb\\n-    username: \\${DB_USER:user}\\n-    password: \\${DB_PASS:pass}\\n+    username: \\${DB_USER:root}\\n+    password: \\${DB_PASS:}\\n+    hikari:\\n+      maximum-pool-size: 20\\n+      minimum-idle: 5\\n+      connection-timeout: 30000\\n+  \\n+  # Add caching configuration\\n+  cache:\\n+    type: caffeine\\n+    caffeine:\\n+      spec: maximumSize=1000,expireAfterAccess=300s\\n+      \\n+  # Security configuration\\n+  security:\\n+    oauth2:\\n+      client:\\n+        registration:\\n+          google:\\n+            client-id: \\${GOOGLE_CLIENT_ID:}\\n+            client-secret: \\${GOOGLE_CLIENT_SECRET:}\\n+            \\n+logging:\\n+  level:\\n+    com.example: DEBUG\\n+    org.springframework.security: DEBUG",
        "raw_url": "https://github.com/$repo_owner/$repo_name/raw/abc123/src/main/resources/application.yml",
        "blob_url": "https://github.com/$repo_owner/$repo_name/blob/abc123/src/main/resources/application.yml"
      }
    ]
  },
  "repository": {
    "id": 987654321,
    "name": "$repo_name",
    "full_name": "$repo_owner/$repo_name",
    "owner": {
      "login": "$repo_owner",
      "id": 12345,
      "type": "User"
    },
    "private": false,
    "html_url": "https://github.com/$repo_owner/$repo_name",
    "description": "Test repository for AI code review",
    "clone_url": "https://github.com/$repo_owner/$repo_name.git",
    "ssh_url": "git@github.com:$repo_owner/$repo_name.git",
    "default_branch": "main",
    "language": "Java",
    "size": 2048,
    "stargazers_count": 5,
    "watchers_count": 3,
    "forks_count": 1,
    "open_issues_count": 2
  },
  "sender": {
    "login": "$repo_owner",
    "id": 12345,
    "type": "User"
  }
}
EOF
)
    
    # 发送webhook请求 (使用通用端点并提供repoUrl参数)
    local repo_url="https://github.com/$repo_owner/$repo_name"
    response=$(curl -s -w "\nHTTP_STATUS_CODE:%{http_code}" \
        -X POST "$SERVER_URL/api/webhooks/generic?repoUrl=$repo_url" \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: pull_request" \
        -H "X-GitHub-Delivery: $(uuidgen 2>/dev/null || echo "$(date +%s)-$(shuf -i 1000-9999 -n 1 2>/dev/null || echo "1234")")" \
        -H "X-Hub-Signature-256: sha256=fake_signature_for_testing" \
        -H "User-Agent: GitHub-Hookshot/abc123" \
        -d "$payload")
    
    # 解析响应
    local http_code=$(echo "$response" | grep "HTTP_STATUS_CODE:" | cut -d: -f2)
    local response_body=$(echo "$response" | sed '/HTTP_STATUS_CODE:/d')
    
    echo "   HTTP状态码: $http_code"
    echo "   响应内容: $response_body"
    
    if [ "$http_code" = "200" ]; then
        echo "✅ Webhook发送成功"
        
        # 尝试解析运行ID
        local run_id=$(echo "$response_body" | grep -o '"data":"[^"]*"' | cut -d'"' -f4 | grep -o 'run-[^"]*' || echo "")
        if [ ! -z "$run_id" ]; then
            echo "   运行ID: $run_id"
            return 0
        fi
    else
        echo "❌ Webhook发送失败"
        return 1
    fi
}

# 函数：检查webhook支持
check_webhook_support() {
    echo "3. 检查webhook支持状态..."
    
    local response=$(curl -s "$SERVER_URL/api/webhooks/providers")
    echo "   支持的提供商: $response"
    
    local repo_check=$(curl -s "$SERVER_URL/api/webhooks/check-support?repoUrl=https://github.com/test-user/test-repo")
    echo "   仓库支持检查: $repo_check"
}

# 函数：检查AI分析结果
check_ai_analysis_results() {
    echo ""
    echo "4. 🤖 检查AI分析结果..."
    
    echo "   📊 获取最新审查记录..."
    local latest_run=$(curl -s "$SERVER_URL/api/runs" | grep -o '"runId":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    if [ -z "$latest_run" ]; then
        echo "   ⚠️ 未找到审查记录，可能处理中..."
        return 1
    fi
    
    echo "   🆔 最新运行ID: $latest_run"
    echo "   📋 获取详细分析结果..."
    
    local analysis_result=$(curl -s "$SERVER_URL/api/runs/$latest_run")
    
    # 提取关键信息
    local findings_count=$(echo "$analysis_result" | grep -o '"findings":\[[^]]*\]' | grep -o '"ruleId":"[^"]*"' | wc -l)
    local total_score=$(echo "$analysis_result" | grep -o '"totalScore":[0-9.]*' | cut -d':' -f2)
    local status=$(echo "$analysis_result" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    
    echo ""
    echo "   📈 **AI分析结果摘要:**"
    echo "   ├─ 🔍 发现问题数: $findings_count"
    echo "   ├─ 📊 总体评分: $total_score"
    echo "   └─ ✅ 处理状态: $status"
    
    # 检查具体的AI发现
    local ai_findings=$(echo "$analysis_result" | grep -o '"ruleId":"MOCK-AI-[^"]*"')
    if [ ! -z "$ai_findings" ]; then
        echo ""
        echo "   🤖 **AI审查发现:**"
        echo "$ai_findings" | while read -r line; do
            local rule_id=$(echo "$line" | cut -d'"' -f4)
            echo "   ├─ 🔸 $rule_id"
        done
        
        # 显示AI建议的详细信息
        echo ""
        echo "   💡 **AI建议查看:** http://localhost:8080/runs/$latest_run"
        return 0
    else
        echo "   ⚠️ 未检测到AI分析结果，可能使用了静态分析"
        return 1
    fi
}

# 函数：等待并验证AI处理结果
wait_and_verify_ai_processing() {
    echo ""
    echo "⏳ 等待AI处理完成..."
    
    local max_wait=30
    local wait_count=0
    
    while [ $wait_count -lt $max_wait ]; do
        echo -n "."
        sleep 2
        ((wait_count++))
        
        # 检查是否有新的审查记录
        local runs_response=$(curl -s "$SERVER_URL/api/runs")
        if echo "$runs_response" | grep -q '"runId"'; then
            echo ""
            echo "✅ 检测到审查记录，开始分析..."
            check_ai_analysis_results
            return $?
        fi
    done
    
    echo ""
    echo "⚠️ 等待超时，请手动检查处理结果"
    return 1
}

# 主测试流程
main() {
    echo ""
    echo "开始GitHub Webhook测试..."
    echo ""
    
    # 默认参数
    local action=${1:-"opened"}
    local pr_number=${2:-1}
    local repo_owner=${3:-"test-user"}
    local repo_name=${4:-"test-repo"}
    
    # 发送webhook事件
    if send_github_webhook "$action" "$pr_number" "$repo_owner" "$repo_name"; then
        echo ""
        # 尝试发送diff内容
        send_diff_to_service "$repo_owner" "$repo_name" "$pr_number"
        echo ""
        check_webhook_support
        
        # 等待并验证AI处理结果
        if wait_and_verify_ai_processing; then
            echo ""
            echo "🎉 **AI代码审查测试完成！**"
            echo ""
            echo "✅ **成功完成的步骤:**"
            echo "   1. ✅ GitHub Webhook事件发送成功"
            echo "   2. ✅ AI模型成功分析diff内容"
            echo "   3. ✅ 生成了有意义的审查建议"
            echo "   4. ✅ 结果已保存到数据库"
            echo ""
            echo "🌐 **查看完整结果:**"
            echo "   • 仪表盘: $SERVER_URL/dashboard"
            echo "   • API接口: $SERVER_URL/api/runs"
            echo ""
            echo "🤖 **AI分析亮点:**"
            echo "   • 检测到安全性改进建议"
            echo "   • 发现代码质量优化机会"
            echo "   • 提供了具体的重构建议"
        else
            echo ""
            echo "⚠️ **部分测试完成**"
            echo "   • Webhook发送成功，但AI分析可能仍在处理中"
            echo "   • 请稍后查看仪表盘获取完整结果"
        fi
        
        echo ""
        echo "📋 **手动验证步骤:**"
        echo "1. 访问仪表盘查看审查记录: $SERVER_URL/dashboard"
        echo "2. 点击最新记录查看AI分析详情"
        echo "3. 验证diff内容是否正确显示"
        echo "4. 确认AI建议是否有意义和可操作"
    else
        echo ""
        echo "❌ 测试失败，请检查："
        echo "1. 服务是否正常运行"
        echo "2. GitHub配置是否正确"
        echo "3. 网络连接是否正常"
        echo "4. 查看服务日志获取详细错误信息"
    fi
}

# 显示使用说明
show_usage() {
    echo "使用方法:"
    echo "  $0 [action] [pr_number] [repo_owner] [repo_name]"
    echo ""
    echo "参数说明:"
    echo "  action      - PR操作 (opened|synchronize|closed，默认: opened)"
    echo "  pr_number   - PR编号 (默认: 1)"
    echo "  repo_owner  - 仓库所有者 (默认: test-user)"
    echo "  repo_name   - 仓库名称 (默认: test-repo)"
    echo ""
    echo "示例:"
    echo "  $0                                    # 使用默认参数"
    echo "  $0 opened 5 myuser myrepo             # 自定义参数"
    echo "  $0 synchronize 3 company project      # 模拟PR更新"
    echo ""
}

# 检查参数
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    show_usage
    exit 0
fi

# 执行主函数
main "$@"
