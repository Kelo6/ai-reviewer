#!/bin/bash

# AI Code Reviewer 启动脚本

echo "🚀 启动 AI Code Reviewer 服务..."

# 设置环境变量
export GITHUB_TOKEN=${GITHUB_TOKEN:-ghp_fake_token_for_development}
export GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID:-disabled}
export GITHUB_CLIENT_SECRET=${GITHUB_CLIENT_SECRET:-disabled}
export GITLAB_TOKEN=${GITLAB_TOKEN:-glpat_fake_token_for_development}
export GITLAB_CLIENT_ID=${GITLAB_CLIENT_ID:-disabled}
export GITLAB_CLIENT_SECRET=${GITLAB_CLIENT_SECRET:-disabled}
export SECURITY_ENABLED=${SECURITY_ENABLED:-false}

# 构建项目
echo "📦 构建项目..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"

# 启动应用
echo "🎯 启动应用服务 (端口: 8080)..."
java -jar target/ai-reviewer-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

echo "🏁 服务已停止"
