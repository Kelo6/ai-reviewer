/**
 * AI Reviewer 图表工具类
 */
class ReviewCharts {
    
    /**
     * 创建雷达图
     */
    static createRadarChart(canvasId, scores, weights) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) {
            console.error('Canvas element not found:', canvasId);
            return null;
        }
        
        // 维度标签映射（中文）
        const dimensionLabels = {
            'SECURITY': '安全性',
            'QUALITY': '代码质量',
            'MAINTAINABILITY': '可维护性',
            'PERFORMANCE': '性能',
            'TEST_COVERAGE': '测试覆盖'
        };
        
        // 维度顺序
        const dimensions = ['SECURITY', 'QUALITY', 'MAINTAINABILITY', 'PERFORMANCE', 'TEST_COVERAGE'];
        
        // 准备数据
        const labels = dimensions.map(dim => dimensionLabels[dim] || dim);
        const scoreData = dimensions.map(dim => scores[dim] || 0);
        const weightData = dimensions.map(dim => (weights[dim] || 0) * 100); // 转换为百分比显示
        
        const data = {
            labels: labels,
            datasets: [
                {
                    label: '得分',
                    data: scoreData,
                    backgroundColor: 'rgba(37, 99, 235, 0.2)',
                    borderColor: 'rgb(37, 99, 235)',
                    borderWidth: 2,
                    pointBackgroundColor: 'rgb(37, 99, 235)',
                    pointBorderColor: '#fff',
                    pointHoverBackgroundColor: '#fff',
                    pointHoverBorderColor: 'rgb(37, 99, 235)'
                },
                {
                    label: '权重 (%)',
                    data: weightData,
                    backgroundColor: 'rgba(100, 116, 139, 0.1)',
                    borderColor: 'rgb(100, 116, 139)',
                    borderWidth: 1,
                    borderDash: [5, 5],
                    pointBackgroundColor: 'rgb(100, 116, 139)',
                    pointBorderColor: '#fff',
                    pointRadius: 3
                }
            ]
        };
        
        const config = {
            type: 'radar',
            data: data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            usePointStyle: true
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const label = context.dataset.label;
                                const value = context.raw;
                                if (label === '权重 (%)') {
                                    return `${label}: ${value.toFixed(1)}%`;
                                } else {
                                    return `${label}: ${value.toFixed(1)}分`;
                                }
                            }
                        }
                    }
                },
                scales: {
                    r: {
                        beginAtZero: true,
                        max: 100,
                        grid: {
                            color: 'rgba(0, 0, 0, 0.1)'
                        },
                        angleLines: {
                            color: 'rgba(0, 0, 0, 0.1)'
                        },
                        pointLabels: {
                            font: {
                                size: 12,
                                weight: '500'
                            },
                            color: 'rgb(15, 23, 42)'
                        },
                        ticks: {
                            display: false,
                            stepSize: 20
                        }
                    }
                },
                interaction: {
                    intersect: false
                }
            }
        };
        
        return new Chart(ctx, config);
    }
    
    /**
     * 创建分数趋势图（柱状图）
     */
    static createScoreBarChart(canvasId, scores, weights) {
        const ctx = document.getElementById(canvasId);
        if (!ctx) {
            console.error('Canvas element not found:', canvasId);
            return null;
        }
        
        const dimensionLabels = {
            'SECURITY': '安全性',
            'QUALITY': '代码质量',
            'MAINTAINABILITY': '可维护性',
            'PERFORMANCE': '性能',
            'TEST_COVERAGE': '测试覆盖'
        };
        
        const dimensions = ['SECURITY', 'QUALITY', 'MAINTAINABILITY', 'PERFORMANCE', 'TEST_COVERAGE'];
        
        const labels = dimensions.map(dim => dimensionLabels[dim] || dim);
        const scoreData = dimensions.map(dim => scores[dim] || 0);
        
        // 根据分数设置颜色
        const backgroundColors = scoreData.map(score => {
            if (score >= 90) return 'rgba(5, 150, 105, 0.8)'; // 绿色
            if (score >= 70) return 'rgba(217, 119, 6, 0.8)';  // 橙色
            return 'rgba(220, 38, 38, 0.8)'; // 红色
        });
        
        const borderColors = scoreData.map(score => {
            if (score >= 90) return 'rgb(5, 150, 105)';
            if (score >= 70) return 'rgb(217, 119, 6)';
            return 'rgb(220, 38, 38)';
        });
        
        const data = {
            labels: labels,
            datasets: [{
                label: '得分',
                data: scoreData,
                backgroundColor: backgroundColors,
                borderColor: borderColors,
                borderWidth: 2,
                borderRadius: 4
            }]
        };
        
        const config = {
            type: 'bar',
            data: data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const dimension = dimensions[context.dataIndex];
                                const weight = (weights[dimension] || 0) * 100;
                                return [
                                    `得分: ${context.raw.toFixed(1)}分`,
                                    `权重: ${weight.toFixed(1)}%`
                                ];
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        max: 100,
                        grid: {
                            color: 'rgba(0, 0, 0, 0.1)'
                        },
                        ticks: {
                            callback: function(value) {
                                return value + '分';
                            }
                        }
                    },
                    x: {
                        grid: {
                            display: false
                        }
                    }
                },
                interaction: {
                    intersect: false
                }
            }
        };
        
        return new Chart(ctx, config);
    }
}

/**
 * HTMX 相关功能
 */
class HTMXHelpers {
    
    /**
     * 初始化HTMX事件监听
     */
    static init() {
        // 请求开始时显示加载状态
        document.body.addEventListener('htmx:beforeRequest', function(evt) {
            const target = evt.target;
            if (target.hasAttribute('hx-indicator')) {
                const indicator = document.querySelector(target.getAttribute('hx-indicator'));
                if (indicator) {
                    indicator.style.display = 'block';
                }
            }
        });
        
        // 请求完成后隐藏加载状态
        document.body.addEventListener('htmx:afterRequest', function(evt) {
            const target = evt.target;
            if (target.hasAttribute('hx-indicator')) {
                const indicator = document.querySelector(target.getAttribute('hx-indicator'));
                if (indicator) {
                    indicator.style.display = 'none';
                }
            }
        });
        
        // 请求错误处理
        document.body.addEventListener('htmx:responseError', function(evt) {
            console.error('HTMX request failed:', evt.detail);
            // 显示错误消息
            const errorMsg = document.createElement('div');
            errorMsg.className = 'alert alert-error';
            errorMsg.textContent = '请求失败，请稍后重试';
            document.body.insertBefore(errorMsg, document.body.firstChild);
            
            // 3秒后自动移除错误消息
            setTimeout(() => {
                errorMsg.remove();
            }, 3000);
        });
    }
}

/**
 * 工具函数
 */
class Utils {
    
    /**
     * 格式化时间戳
     */
    static formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
    
    /**
     * 格式化持续时间（秒）
     */
    static formatDuration(seconds) {
        if (seconds < 60) {
            return `${seconds}秒`;
        } else if (seconds < 3600) {
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = seconds % 60;
            return `${minutes}分${remainingSeconds}秒`;
        } else {
            const hours = Math.floor(seconds / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            return `${hours}时${minutes}分`;
        }
    }
    
    /**
     * 获取严重性对应的CSS类
     */
    static getSeverityClass(severity) {
        switch (severity?.toLowerCase()) {
            case 'critical': return 'severity-critical';
            case 'major': return 'severity-major';
            case 'minor': return 'severity-minor';
            case 'info': return 'severity-info';
            default: return 'badge-secondary';
        }
    }
    
    /**
     * 获取分数对应的颜色类
     */
    static getScoreClass(score) {
        if (score >= 90) return 'badge-success';
        if (score >= 70) return 'badge-warning';
        return 'badge-error';
    }
    
    /**
     * 复制文本到剪贴板
     */
    static async copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            console.error('Failed to copy text:', err);
            return false;
        }
    }
    
    /**
     * 显示临时消息
     */
    static showMessage(message, type = 'info', duration = 3000) {
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type}`;
        alertDiv.textContent = message;
        alertDiv.style.position = 'fixed';
        alertDiv.style.top = '20px';
        alertDiv.style.right = '20px';
        alertDiv.style.zIndex = '9999';
        alertDiv.style.maxWidth = '400px';
        
        document.body.appendChild(alertDiv);
        
        setTimeout(() => {
            alertDiv.remove();
        }, duration);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    HTMXHelpers.init();
    
    // 为所有带有data-copy属性的元素添加复制功能
    document.querySelectorAll('[data-copy]').forEach(element => {
        element.addEventListener('click', async function() {
            const text = this.dataset.copy || this.textContent;
            const success = await Utils.copyToClipboard(text);
            if (success) {
                Utils.showMessage('已复制到剪贴板', 'success');
            } else {
                Utils.showMessage('复制失败', 'error');
            }
        });
    });
});

// 导出到全局作用域
window.ReviewCharts = ReviewCharts;
window.HTMXHelpers = HTMXHelpers;
window.Utils = Utils;
