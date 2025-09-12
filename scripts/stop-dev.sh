#!/bin/bash

# AI Code Reviewer - 开发环境停止脚本

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

# 停止指定端口的服务
stop_service_by_port() {
    local port=$1
    local service_name=$2
    
    log_info "检查端口 $port 上的 $service_name 服务..."
    
    local pids=$(lsof -ti:$port 2>/dev/null || true)
    
    if [ -z "$pids" ]; then
        log_info "$service_name 服务未在端口 $port 上运行"
        return 0
    fi
    
    log_info "发现 $service_name 服务进程: $pids"
    
    # 先尝试优雅停止
    for pid in $pids; do
        if kill -TERM "$pid" 2>/dev/null; then
            log_info "发送 SIGTERM 信号到进程 $pid"
        fi
    done
    
    # 等待进程停止
    local max_wait=10
    local wait_count=0
    
    while [ $wait_count -lt $max_wait ]; do
        local remaining_pids=$(lsof -ti:$port 2>/dev/null || true)
        if [ -z "$remaining_pids" ]; then
            log_success "$service_name 服务已停止"
            return 0
        fi
        sleep 1
        wait_count=$((wait_count + 1))
        echo -n "."
    done
    
    echo ""
    log_warning "$service_name 服务未能优雅停止，强制终止..."
    
    # 强制停止
    local remaining_pids=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$remaining_pids" ]; then
        for pid in $remaining_pids; do
            if kill -KILL "$pid" 2>/dev/null; then
                log_info "强制终止进程 $pid"
            fi
        done
        log_success "$service_name 服务已强制停止"
    fi
}

# 停止所有相关服务
stop_all_services() {
    log_info "停止所有 AI Code Reviewer 服务..."
    
    # 从环境变量获取端口，或使用默认值
    local backend_port=${BACKEND_PORT:-8080}
    local frontend_port=${FRONTEND_PORT:-8081}
    
    # 停止后端服务
    stop_service_by_port $backend_port "后端"
    
    # 停止前端服务
    stop_service_by_port $frontend_port "前端"
    
    # 清理可能的僵尸进程
    local java_pids=$(pgrep -f "ai-reviewer.*\.jar" 2>/dev/null || true)
    if [ -n "$java_pids" ]; then
        log_warning "发现可能的僵尸进程: $java_pids"
        for pid in $java_pids; do
            if kill -TERM "$pid" 2>/dev/null; then
                log_info "清理僵尸进程 $pid"
            fi
        done
    fi
}

# 显示运行状态
show_status() {
    local backend_port=${BACKEND_PORT:-8080}
    local frontend_port=${FRONTEND_PORT:-8081}
    
    log_info "AI Code Reviewer 服务状态:"
    
    # 检查后端状态
    if lsof -Pi :$backend_port -sTCP:LISTEN -t >/dev/null 2>&1; then
        local backend_pid=$(lsof -ti:$backend_port 2>/dev/null)
        echo -e "  后端服务 (端口 $backend_port): ${GREEN}运行中${NC} (PID: $backend_pid)"
    else
        echo -e "  后端服务 (端口 $backend_port): ${RED}已停止${NC}"
    fi
    
    # 检查前端状态
    if lsof -Pi :$frontend_port -sTCP:LISTEN -t >/dev/null 2>&1; then
        local frontend_pid=$(lsof -ti:$frontend_port 2>/dev/null)
        echo -e "  前端服务 (端口 $frontend_port): ${GREEN}运行中${NC} (PID: $frontend_pid)"
    else
        echo -e "  前端服务 (端口 $frontend_port): ${RED}已停止${NC}"
    fi
}

# 显示帮助信息
show_help() {
    echo "AI Code Reviewer - 开发环境停止脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [stop|status|help]"
    echo ""
    echo "选项:"
    echo "  stop      - 停止所有服务 (默认)"
    echo "  status    - 显示服务状态"
    echo "  help      - 显示此帮助信息"
    echo ""
    echo "环境变量:"
    echo "  BACKEND_PORT  - 后端服务端口 (默认: 8080)"
    echo "  FRONTEND_PORT - 前端服务端口 (默认: 8081)"
    echo ""
    echo "示例:"
    echo "  $0              # 停止所有服务"
    echo "  $0 stop         # 停止所有服务"
    echo "  $0 status       # 查看服务状态"
}

# 主函数
main() {
    local action=${1:-stop}
    
    # 加载环境变量
    if [ -f "scripts/dev.env" ]; then
        source scripts/dev.env
    fi
    
    case $action in
        help|-h|--help)
            show_help
            exit 0
            ;;
        stop)
            log_info "AI Code Reviewer 开发环境停止脚本"
            stop_all_services
            log_success "所有服务已停止"
            ;;
        status)
            show_status
            ;;
        *)
            log_error "未知选项: $action"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
