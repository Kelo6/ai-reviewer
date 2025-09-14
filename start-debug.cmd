@echo off
echo Starting AI Reviewer application...
echo.

rem 设置数据库连接参数
set DB_URL=jdbc:mysql://localhost:3306/ai_reviewer?useUnicode=true^&characterEncoding=UTF-8^&useSSL=false^&allowPublicKeyRetrieval=true^&serverTimezone=UTC^&createDatabaseIfNotExist=true
set DB_USER=root
set DB_PASS=root

rem 设置安全配置
set SECURITY_ENABLED=false

rem 禁用 GitHub 集成（提供虚假token以通过验证）
set GITHUB_TOKEN=ghp_fake_token_for_development
set GITHUB_CLIENT_ID=disabled
set GITHUB_CLIENT_SECRET=disabled

rem 禁用 GitLab 集成（提供虚假token以通过验证）
set GITLAB_TOKEN=glpat_fake_token_for_development
set GITLAB_CLIENT_ID=disabled
set GITLAB_CLIENT_SECRET=disabled

echo Environment variables set.
echo.
echo Starting application with debug logging...
echo.

java -Dspring.profiles.active=dev -Dlogging.level.com.ai.reviewer=DEBUG -jar target/ai-reviewer-1.0.0-SNAPSHOT.jar

pause
