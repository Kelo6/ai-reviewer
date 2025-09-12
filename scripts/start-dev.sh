#!/bin/bash

# AI Code Reviewer - 开发环境启动脚本
# 
# 使用方法:
#   ./scripts/start-dev.sh [backend|frontend|both]
#
# 默认启动前后端服务

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数定义
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否存在环境变量文件
check_env_file() {
    if [ -f "scripts/dev.env" ]; then
        log_info "加载环境变量文件: scripts/dev.env"
        source scripts/dev.env
    else
        log_warning "环境变量文件不存在: scripts/dev.env"
        log_info "请复制 scripts/dev.env.example 并配置环境变量"
        log_info "cp scripts/dev.env.example scripts/dev.env"
        log_info "继续使用默认配置..."
    fi
}

# 检查 JAR 文件是否存在
check_jar_files() {
    if [ ! -f app-backend/target/*.jar ] && [ ! -f app-frontend/target/*.jar ]; then
        log_error "未找到可执行 JAR 文件，请先构建项目:"
        log_info "mvn -B -ntp clean package -DskipTests"
        exit 1
    fi
}

# 检查端口是否被占用
check_port() {
    local port=$1
    local service=$2
    
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        log_warning "端口 $port 已被占用 ($service)"
        log_info "可能的解决方案:"
        log_info "  - 停止占用端口的进程: kill \$(lsof -ti:$port)"
        log_info "  - 修改环境变量 ${service}_PORT"
        return 1
    fi
    return 0
}

# 创建日志目录
create_log_dir() {
    if [ ! -d "logs" ]; then
        mkdir -p logs
        log_info "创建日志目录: logs/"
    fi
}

# 启动后端服务
start_backend() {
    local backend_port=${BACKEND_PORT:-8080}
    
    log_info "准备启动后端服务..."
    
    # 检查端口
    if ! check_port $backend_port "BACKEND"; then
        return 1
    fi
    
    # 查找 JAR 文件
    local backend_jar=$(find app-backend/target -name "*.jar" -not -name "*-sources.jar" | head -n 1)
    if [ -z "$backend_jar" ]; then
        log_error "未找到后端 JAR 文件"
        return 1
    fi
    
    log_info "启动后端服务: $backend_jar"
    log_info "端口: $backend_port"
    
    # 后台启动
    nohup java -jar "$backend_jar" > logs/backend.log 2>&1 &
    local backend_pid=$!
    
    log_success "后端服务启动中... PID: $backend_pid"
    
    # 等待服务启动
    log_info "等待后端服务启动..."
    local max_wait=30
    local wait_count=0
    
    while [ $wait_count -lt $max_wait ]; do
        if curl -sf "http://localhost:$backend_port/api/health" >/dev/null 2>&1; then
            log_success "后端服务启动成功!"
            log_info "健康检查: http://localhost:$backend_port/api/health"
            return 0
        fi
        sleep 2
        wait_count=$((wait_count + 1))
        echo -n "."
    done
    
    echo ""
    log_error "后端服务启动超时，请检查日志: logs/backend.log"
    return 1
}

# 启动前端服务
start_frontend() {
    local frontend_port=${FRONTEND_PORT:-8081}
    
    log_info "准备启动前端服务..."
    
    # 检查端口
    if ! check_port $frontend_port "FRONTEND"; then
        return 1
    fi
    
    # 查找 JAR 文件
    local frontend_jar=$(find app-frontend/target -name "*.jar" -not -name "*-sources.jar" | head -n 1)
    if [ -z "$frontend_jar" ]; then
        log_error "未找到前端 JAR 文件"
        return 1
    fi
    
    log_info "启动前端服务: $frontend_jar"
    log_info "端口: $frontend_port"
    
    # 后台启动
    nohup java -jar "$frontend_jar" > logs/frontend.log 2>&1 &
    local frontend_pid=$!
    
    log_success "前端服务启动中... PID: $frontend_pid"
    
    # 等待服务启动
    log_info "等待前端服务启动..."
    local max_wait=20
    local wait_count=0
    
    while [ $wait_count -lt $max_wait ]; do
        if curl -sf "http://localhost:$frontend_port" >/dev/null 2>&1; then
            log_success "前端服务启动成功!"
            log_info "前端应用: http://localhost:$frontend_port"
            return 0
        fi
        sleep 2
        wait_count=$((wait_count + 1))
        echo -n "."
    done
    
    echo ""
    log_error "前端服务启动超时，请检查日志: logs/frontend.log"
    return 1
}

# 显示帮助信息
show_help() {
    echo "AI Code Reviewer - 开发环境启动脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [backend|frontend|both|help]"
    echo ""
    echo "选项:"
    echo "  backend   - 仅启动后端服务 (端口 8080)"
    echo "  frontend  - 仅启动前端服务 (端口 8081)"
    echo "  both      - 启动前后端服务 (默认)"
    echo "  help      - 显示此帮助信息"
    echo ""
    echo "环境变量:"
    echo "  BACKEND_PORT  - 后端服务端口 (默认: 8080)"
    echo "  FRONTEND_PORT - 前端服务端口 (默认: 8081)"
    echo ""
    echo "配置文件:"
    echo "  scripts/dev.env - 环境变量配置文件"
    echo ""
    echo "示例:"
    echo "  $0                 # 启动前后端服务"
    echo "  $0 backend         # 仅启动后端"
    echo "  $0 frontend        # 仅启动前端"
}

# 主函数
main() {
    local mode=${1:-both}
    
    case $mode in
        help|-h|--help)
            show_help
            exit 0
            ;;
        backend|frontend|both)
            ;;
        *)
            log_error "未知选项: $mode"
            show_help
            exit 1
            ;;
    esac
    
    log_info "AI Code Reviewer 开发环境启动脚本"
    log_info "模式: $mode"
    
    # 检查环境
    check_env_file
    create_log_dir
    check_jar_files
    
    # 根据模式启动服务
    case $mode in
        backend)
            if start_backend; then
                log_success "后端服务启动完成!"
            else
                exit 1
            fi
            ;;
        frontend)
            if start_frontend; then
                log_success "前端服务启动完成!"
            else
                exit 1
            fi
            ;;
        both)
            if start_backend && start_frontend; then
                log_success "所有服务启动完成!"
                echo ""
                log_info "访问地址:"
                log_info "  前端应用: http://localhost:${FRONTEND_PORT:-8081}"
                log_info "  后端 API: http://localhost:${BACKEND_PORT:-8080}/api"
                log_info "  健康检查: http://localhost:${BACKEND_PORT:-8080}/api/health"
                echo ""
                log_info "日志文件:"
                log_info "  后端日志: logs/backend.log"
                log_info "  前端日志: logs/frontend.log"
                echo ""
                log_info "停止服务: ./scripts/stop-dev.sh"
            else
                exit 1
            fi
            ;;
    esac
}

# 执行主函数
main "$@"
