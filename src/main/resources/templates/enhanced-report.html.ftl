<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI 代码评审报告 - ${reviewRun.repo().name()}</title>
    
    <!-- Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    
    <!-- Chart.js -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    
    <!-- Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            color: #1f2937;
            background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
            min-height: 100vh;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }

        /* Header Styles */
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            border-radius: 16px;
            margin-bottom: 30px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.1);
            position: relative;
            overflow: hidden;
        }

        .header::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse"><path d="M 10 0 L 0 0 0 10" fill="none" stroke="white" stroke-width="0.5" opacity="0.1"/></pattern></defs><rect width="100" height="100" fill="url(%23grid)"/></svg>');
            opacity: 0.1;
        }

        .header-content {
            position: relative;
            z-index: 1;
        }

        .header h1 {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .header .subtitle {
            font-size: 1.2rem;
            opacity: 0.9;
            margin-bottom: 20px;
        }

        .header-info {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-top: 30px;
        }

        .info-item {
            background: rgba(255,255,255,0.1);
            padding: 15px;
            border-radius: 12px;
            backdrop-filter: blur(10px);
        }

        .info-item .label {
            font-size: 0.9rem;
            opacity: 0.8;
            margin-bottom: 5px;
        }

        .info-item .value {
            font-size: 1.1rem;
            font-weight: 600;
        }

        /* Score Dashboard */
        .score-dashboard {
            background: white;
            border-radius: 16px;
            padding: 40px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }

        .score-main {
            text-align: center;
            margin-bottom: 40px;
        }

        .score-circle {
            width: 200px;
            height: 200px;
            margin: 0 auto 20px;
            position: relative;
        }

        .score-value {
            font-size: 3.5rem;
            font-weight: 700;
            margin-bottom: 10px;
        }

        .score-grade {
            font-size: 1.5rem;
            font-weight: 600;
            margin-bottom: 10px;
        }

        .score-description {
            font-size: 1.1rem;
            color: #6b7280;
        }

        /* Dimension Scores */
        .dimensions-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-top: 30px;
        }

        .dimension-card {
            background: white;
            border-radius: 12px;
            padding: 25px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.08);
            border-left: 4px solid;
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .dimension-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 25px rgba(0,0,0,0.15);
        }

        .dimension-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }

        .dimension-name {
            font-size: 1.2rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .dimension-score {
            font-size: 1.8rem;
            font-weight: 700;
        }

        .progress-bar {
            height: 8px;
            background: #f1f5f9;
            border-radius: 4px;
            overflow: hidden;
            margin: 10px 0;
        }

        .progress-fill {
            height: 100%;
            border-radius: 4px;
            transition: width 0.8s ease;
        }

        .dimension-meta {
            display: flex;
            justify-content: space-between;
            font-size: 0.9rem;
            color: #6b7280;
            margin-top: 10px;
        }

        /* Statistics */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }

        .stat-card {
            background: white;
            border-radius: 12px;
            padding: 25px;
            text-align: center;
            box-shadow: 0 4px 15px rgba(0,0,0,0.08);
            border-top: 4px solid;
            transition: transform 0.2s;
        }

        .stat-card:hover {
            transform: translateY(-2px);
        }

        .stat-icon {
            font-size: 2.5rem;
            margin-bottom: 10px;
        }

        .stat-value {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 5px;
        }

        .stat-label {
            font-size: 1rem;
            color: #6b7280;
            font-weight: 500;
        }

        /* Findings */
        .findings-section {
            background: white;
            border-radius: 16px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }

        .findings-header {
            display: flex;
            justify-content: between;
            align-items: center;
            margin-bottom: 30px;
        }

        .findings-title {
            font-size: 1.8rem;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .severity-filters {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
        }

        .filter-btn {
            padding: 8px 16px;
            border: 2px solid #e5e7eb;
            border-radius: 8px;
            background: white;
            cursor: pointer;
            transition: all 0.2s;
            font-weight: 500;
        }

        .filter-btn.active {
            border-color: #3b82f6;
            background: #3b82f6;
            color: white;
        }

        .finding-card {
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            margin-bottom: 20px;
            overflow: hidden;
            transition: all 0.2s;
        }

        .finding-card:hover {
            border-color: #d1d5db;
            box-shadow: 0 4px 15px rgba(0,0,0,0.08);
        }

        .finding-header {
            padding: 20px;
            background: #f9fafb;
            border-bottom: 1px solid #e5e7eb;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .finding-title {
            font-size: 1.1rem;
            font-weight: 600;
            margin-bottom: 5px;
        }

        .finding-meta {
            font-size: 0.9rem;
            color: #6b7280;
        }

        .finding-severity {
            padding: 4px 12px;
            border-radius: 6px;
            font-size: 0.8rem;
            font-weight: 600;
            text-transform: uppercase;
        }

        .finding-content {
            padding: 20px;
        }

        .finding-description {
            margin-bottom: 15px;
            line-height: 1.6;
        }

        .finding-suggestion {
            background: #f0f9ff;
            border-left: 4px solid #3b82f6;
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 15px;
        }

        .finding-code {
            background: #1f2937;
            color: #f9fafb;
            padding: 15px;
            border-radius: 8px;
            font-family: 'Monaco', 'Courier New', monospace;
            font-size: 0.9rem;
            overflow-x: auto;
            margin-bottom: 15px;
        }

        .finding-sources {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }

        .source-tag {
            background: #e5e7eb;
            color: #374151;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.8rem;
        }

        /* Charts */
        .charts-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
            gap: 30px;
            margin-bottom: 30px;
        }

        .chart-card {
            background: white;
            border-radius: 16px;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }

        .chart-title {
            font-size: 1.4rem;
            font-weight: 600;
            margin-bottom: 20px;
            text-align: center;
        }

        /* Recommendations */
        .recommendations {
            background: white;
            border-radius: 16px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }

        .recommendation-item {
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 20px;
            border-left: 4px solid;
        }

        .recommendation-title {
            font-size: 1.2rem;
            font-weight: 600;
            margin-bottom: 10px;
        }

        .recommendation-description {
            color: #6b7280;
            line-height: 1.6;
        }

        /* Footer */
        .footer {
            text-align: center;
            padding: 40px;
            color: #6b7280;
            background: white;
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
        }

        /* Color Classes */
        .excellent { color: #10b981; border-color: #10b981; }
        .good { color: #3b82f6; border-color: #3b82f6; }
        .fair { color: #f59e0b; border-color: #f59e0b; }
        .poor { color: #ef4444; border-color: #ef4444; }
        .critical { color: #dc2626; border-color: #dc2626; }

        .bg-excellent { background-color: #ecfdf5; }
        .bg-good { background-color: #dbeafe; }
        .bg-fair { background-color: #fffbeb; }
        .bg-poor { background-color: #fef2f2; }
        .bg-critical { background-color: #fee2e2; }

        /* Responsive */
        @media (max-width: 768px) {
            .container {
                padding: 10px;
            }
            
            .header {
                padding: 20px;
            }
            
            .header h1 {
                font-size: 2rem;
            }
            
            .score-circle {
                width: 150px;
                height: 150px;
            }
            
            .score-value {
                font-size: 2.5rem;
            }
            
            .dimensions-grid {
                grid-template-columns: 1fr;
            }
            
            .charts-grid {
                grid-template-columns: 1fr;
            }
        }

        /* Animations */
        @keyframes fadeInUp {
            from {
                opacity: 0;
                transform: translateY(30px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        .fade-in-up {
            animation: fadeInUp 0.6s ease-out;
        }

        /* Print Styles */
        @media print {
            body {
                background: white !important;
            }
            
            .container {
                max-width: none !important;
                padding: 0 !important;
            }
            
            .header {
                background: #667eea !important;
                -webkit-print-color-adjust: exact;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header fade-in-up">
            <div class="header-content">
                <h1>
                    <i class="fas fa-robot"></i>
                    AI 代码评审报告
                </h1>
                <div class="subtitle">智能化代码质量分析与改进建议</div>
                
                <div class="header-info">
                    <div class="info-item">
                        <div class="label">仓库</div>
                        <div class="value">${reviewRun.repo().owner()}/${reviewRun.repo().name()}</div>
                    </div>
                    <div class="info-item">
                        <div class="label">Pull Request</div>
                        <div class="value">#${reviewRun.pull().number()}</div>
                    </div>
                    <div class="info-item">
                        <div class="label">生成时间</div>
                        <div class="value">${generatedAt}</div>
                    </div>
                    <div class="info-item">
                        <div class="label">运行ID</div>
                        <div class="value">${reviewRun.runId()}</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Score Dashboard -->
        <div class="score-dashboard fade-in-up">
            <div class="score-main">
                <div class="score-circle">
                    <canvas id="scoreChart" width="200" height="200"></canvas>
                </div>
                
                <#assign totalScore = reviewRun.scores().totalScore()>
                <#assign scoreClass = "">
                <#assign scoreGrade = "">
                <#if (totalScore >= 90)>
                    <#assign scoreClass = "excellent">
                    <#assign scoreGrade = "优秀">
                <#elseif (totalScore >= 75)>
                    <#assign scoreClass = "good">
                    <#assign scoreGrade = "良好">
                <#elseif (totalScore >= 60)>
                    <#assign scoreClass = "fair">
                    <#assign scoreGrade = "一般">
                <#elseif (totalScore >= 40)>
                    <#assign scoreClass = "poor">
                    <#assign scoreGrade = "较差">
                <#else>
                    <#assign scoreClass = "critical">
                    <#assign scoreGrade = "严重">
                </#if>
                
                <div class="score-value ${scoreClass}">${totalScore?string("0.0")}</div>
                <div class="score-grade ${scoreClass}">${scoreGrade}</div>
                <div class="score-description">
                    <#if (totalScore >= 90)>
                        代码质量优秀，展现了高水平的工程实践
                    <#elseif (totalScore >= 75)>
                        代码质量良好，符合行业标准要求
                    <#elseif (totalScore >= 60)>
                        代码质量一般，建议进行改进
                    <#elseif (totalScore >= 40)>
                        代码质量较差，需要重点改进
                    <#else>
                        代码质量存在严重问题，需要立即处理
                    </#if>
                </div>
            </div>

            <!-- Dimension Scores -->
            <div class="dimensions-grid">
                <#assign weights = reviewRun.scores().weights()>
                <#list reviewRun.scores().dimensions() as dimension, score>
                    <#assign weight = weights[dimension]!0.0>
                    <#assign dimensionClass = "">
                    <#assign dimensionIcon = "">
                    <#assign dimensionName = "">
                    <#if (score >= 90)>
                        <#assign dimensionClass = "excellent">
                    <#elseif (score >= 75)>
                        <#assign dimensionClass = "good">
                    <#elseif (score >= 60)>
                        <#assign dimensionClass = "fair">
                    <#elseif (score >= 40)>
                        <#assign dimensionClass = "poor">
                    <#else>
                        <#assign dimensionClass = "critical">
                    </#if>
                    
                    <#if dimension.name() == "SECURITY">
                        <#assign dimensionIcon = "fas fa-shield-alt">
                        <#assign dimensionName = "安全性">
                    <#elseif dimension.name() == "QUALITY">
                        <#assign dimensionIcon = "fas fa-gem">
                        <#assign dimensionName = "代码质量">
                    <#elseif dimension.name() == "MAINTAINABILITY">
                        <#assign dimensionIcon = "fas fa-tools">
                        <#assign dimensionName = "可维护性">
                    <#elseif dimension.name() == "PERFORMANCE">
                        <#assign dimensionIcon = "fas fa-tachometer-alt">
                        <#assign dimensionName = "性能">
                    <#elseif dimension.name() == "TEST_COVERAGE">
                        <#assign dimensionIcon = "fas fa-flask">
                        <#assign dimensionName = "测试覆盖率">
                    <#else>
                        <#assign dimensionIcon = "fas fa-code">
                        <#assign dimensionName = dimension?replace("_", " ")?capitalize>
                    </#if>
                    
                    <div class="dimension-card ${dimensionClass}">
                        <div class="dimension-header">
                            <div class="dimension-name">
                                <i class="${dimensionIcon}"></i>
                                ${dimensionName}
                            </div>
                            <div class="dimension-score ${dimensionClass}">${score?string("0.0")}</div>
                        </div>
                        
                        <div class="progress-bar">
                            <div class="progress-fill ${dimensionClass}" style="width: ${score}%; background-color: var(--dimension-color);"></div>
                        </div>
                        
                        <div class="dimension-meta">
                            <span>权重: ${(weight * 100)?string("0.0")}%</span>
                            <span>贡献: ${(score * weight)?string("0.0")}分</span>
                        </div>
                    </div>
                </#list>
            </div>
        </div>

        <!-- Statistics -->
        <div class="stats-grid fade-in-up">
            <div class="stat-card" style="border-top-color: #3b82f6;">
                <div class="stat-icon" style="color: #3b82f6;">
                    <i class="fas fa-file-code"></i>
                </div>
                <div class="stat-value" style="color: #3b82f6;">${reviewRun.stats().filesChanged()}</div>
                <div class="stat-label">变更文件</div>
            </div>
            
            <div class="stat-card" style="border-top-color: #10b981;">
                <div class="stat-icon" style="color: #10b981;">
                    <i class="fas fa-plus"></i>
                </div>
                <div class="stat-value" style="color: #10b981;">+${reviewRun.stats().linesAdded()}</div>
                <div class="stat-label">新增行数</div>
            </div>
            
            <div class="stat-card" style="border-top-color: #ef4444;">
                <div class="stat-icon" style="color: #ef4444;">
                    <i class="fas fa-minus"></i>
                </div>
                <div class="stat-value" style="color: #ef4444;">-${reviewRun.stats().linesDeleted()}</div>
                <div class="stat-label">删除行数</div>
            </div>
            
            <div class="stat-card" style="border-top-color: #f59e0b;">
                <div class="stat-icon" style="color: #f59e0b;">
                    <i class="fas fa-clock"></i>
                </div>
                <div class="stat-value" style="color: #f59e0b;">${reviewRun.stats().latencyMs()}ms</div>
                <div class="stat-label">处理耗时</div>
            </div>
            
            <#if reviewRun.stats().tokenCostUsd()??>
            <div class="stat-card" style="border-top-color: #8b5cf6;">
                <div class="stat-icon" style="color: #8b5cf6;">
                    <i class="fas fa-dollar-sign"></i>
                </div>
                <div class="stat-value" style="color: #8b5cf6;">$${reviewRun.stats().tokenCostUsd()?string("0.00")}</div>
                <div class="stat-label">API成本</div>
            </div>
            </#if>
            
            <div class="stat-card" style="border-top-color: #6366f1;">
                <div class="stat-icon" style="color: #6366f1;">
                    <i class="fas fa-search"></i>
                </div>
                <div class="stat-value" style="color: #6366f1;">${reviewRun.findings()?size}</div>
                <div class="stat-label">发现问题</div>
            </div>
        </div>

        <!-- Charts -->
        <div class="charts-grid fade-in-up">
            <div class="chart-card">
                <div class="chart-title">问题严重性分布</div>
                <canvas id="severityChart" width="400" height="300"></canvas>
            </div>
            
            <div class="chart-card">
                <div class="chart-title">问题维度分布</div>
                <canvas id="dimensionChart" width="400" height="300"></canvas>
            </div>
        </div>

        <!-- Findings -->
        <div class="findings-section fade-in-up">
            <div class="findings-header">
                <div class="findings-title">
                    <i class="fas fa-bug"></i>
                    发现的问题
                </div>
            </div>
            
            <div class="severity-filters">
                <button class="filter-btn active" onclick="filterFindings('all')">全部</button>
                <button class="filter-btn" onclick="filterFindings('critical')">严重</button>
                <button class="filter-btn" onclick="filterFindings('major')">重要</button>
                <button class="filter-btn" onclick="filterFindings('minor')">次要</button>
                <button class="filter-btn" onclick="filterFindings('info')">信息</button>
            </div>
            
            <div id="findingsContainer">
                <#if reviewRun.findings()?size == 0>
                    <div style="text-align: center; padding: 60px; color: #10b981;">
                        <i class="fas fa-check-circle" style="font-size: 4rem; margin-bottom: 20px;"></i>
                        <h3>太棒了！未发现任何问题</h3>
                        <p>代码质量很棒，继续保持！</p>
                    </div>
                <#else>
                    <#list reviewRun.findings() as finding>
                        <#assign severityClass = "">
                        <#assign severityColor = "">
                        <#if finding.severity().name() == "CRITICAL">
                            <#assign severityClass = "critical">
                            <#assign severityColor = "#dc2626">
                        <#elseif finding.severity().name() == "MAJOR">
                            <#assign severityClass = "major">
                            <#assign severityColor = "#ea580c">
                        <#elseif finding.severity().name() == "MINOR">
                            <#assign severityClass = "minor">
                            <#assign severityColor = "#f59e0b">
                        <#else>
                            <#assign severityClass = "info">
                            <#assign severityColor = "#3b82f6">
                        </#if>
                        
                        <div class="finding-card finding-${severityClass}" data-severity="${finding.severity().name()?lower_case}">
                            <div class="finding-header">
                                <div>
                                    <div class="finding-title">${finding.title()}</div>
                                    <div class="finding-meta">
                                        <i class="fas fa-file"></i> ${finding.file()}:${finding.startLine()}
                                        <span style="margin-left: 15px;">
                                            <i class="fas fa-layer-group"></i> ${finding.dimension()?replace("_", " ")?capitalize}
                                        </span>
                                        <span style="margin-left: 15px;">
                                            <i class="fas fa-percentage"></i> ${(finding.confidence() * 100)?string("0")}% 置信度
                                        </span>
                                    </div>
                                </div>
                                <div class="finding-severity" style="background-color: ${severityColor}; color: white;">
                                    ${finding.severity().name()}
                                </div>
                            </div>
                            
                            <div class="finding-content">
                                <div class="finding-description">
                                    <strong>问题描述：</strong>
                                    ${finding.evidence()}
                                </div>
                                
                                <div class="finding-suggestion">
                                    <strong><i class="fas fa-lightbulb"></i> 建议方案：</strong>
                                    ${finding.suggestion()}
                                </div>
                                
                                <#if finding.patch()??>
                                <div class="finding-code">
                                    <strong>建议代码修改：</strong>
                                    <pre><code>${finding.patch()}</code></pre>
                                </div>
                                </#if>
                                
                                <div>
                                    <strong>检测来源：</strong>
                                    <div class="finding-sources">
                                        <#list finding.sources() as source>
                                            <span class="source-tag">${source}</span>
                                        </#list>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </#list>
                </#if>
            </div>
        </div>

        <!-- Recommendations -->
        <div class="recommendations fade-in-up">
            <h2 style="margin-bottom: 30px;">
                <i class="fas fa-bullseye"></i>
                改进建议
            </h2>
            
            <#if (totalScore >= 90)>
                <div class="recommendation-item bg-excellent" style="border-left-color: #10b981;">
                    <div class="recommendation-title" style="color: #10b981;">
                        <i class="fas fa-star"></i>
                        卓越表现！
                    </div>
                    <div class="recommendation-description">
                        您的代码质量已经达到了优秀标准！继续保持良好的编码实践，关注新兴技术和最佳实践。
                    </div>
                </div>
            <#elseif (totalScore >= 75)>
                <div class="recommendation-item bg-good" style="border-left-color: #3b82f6;">
                    <div class="recommendation-title" style="color: #3b82f6;">
                        <i class="fas fa-thumbs-up"></i>
                        表现良好
                    </div>
                    <div class="recommendation-description">
                        代码质量良好，建议继续改进低分维度，加强代码审查和测试覆盖率。
                    </div>
                </div>
            <#elseif (totalScore >= 60)>
                <div class="recommendation-item bg-fair" style="border-left-color: #f59e0b;">
                    <div class="recommendation-title" style="color: #f59e0b;">
                        <i class="fas fa-exclamation-triangle"></i>
                        有待改进
                    </div>
                    <div class="recommendation-description">
                        代码质量一般，建议重点关注安全性和可维护性，制定系统性的改进计划。
                    </div>
                </div>
            <#else>
                <div class="recommendation-item bg-critical" style="border-left-color: #dc2626;">
                    <div class="recommendation-title" style="color: #dc2626;">
                        <i class="fas fa-exclamation-circle"></i>
                        需要立即改进
                    </div>
                    <div class="recommendation-description">
                        代码质量存在严重问题，建议立即暂停新功能开发，专注于质量修复和团队培训。
                    </div>
                </div>
            </#if>
        </div>

        <!-- Footer -->
        <div class="footer fade-in-up">
            <div style="margin-bottom: 20px;">
                <h3>🤖 AI-Reviewer</h3>
                <p>智能化代码审查工具，助力团队提升代码质量</p>
            </div>
            <div>
                <strong>版本：</strong>1.0.0 |
                <strong>生成时间：</strong>${generatedAt}
            </div>
        </div>
    </div>

    <script>
        // Initialize charts and interactions
        document.addEventListener('DOMContentLoaded', function() {
            initializeScoreChart();
            initializeSeverityChart();
            initializeDimensionChart();
            initializeAnimations();
        });

        function initializeScoreChart() {
            const ctx = document.getElementById('scoreChart').getContext('2d');
            const score = ${reviewRun.scores().totalScore()};
            
            new Chart(ctx, {
                type: 'doughnut',
                data: {
                    datasets: [{
                        data: [score, 100 - score],
                        backgroundColor: [
                            score >= 90 ? '#10b981' : 
                            score >= 75 ? '#3b82f6' : 
                            score >= 60 ? '#f59e0b' : 
                            score >= 40 ? '#ef4444' : '#dc2626',
                            '#f1f5f9'
                        ],
                        borderWidth: 0
                    }]
                },
                options: {
                    responsive: false,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: false
                        }
                    },
                    cutout: '70%'
                }
            });
        }

        function initializeSeverityChart() {
            const ctx = document.getElementById('severityChart').getContext('2d');
            
            // Count findings by severity
            const severityCounts = {
                critical: ${(reviewRun.findings()?filter(f -> f.severity().name() == "CRITICAL"))?size},
                major: ${(reviewRun.findings()?filter(f -> f.severity().name() == "MAJOR"))?size},
                minor: ${(reviewRun.findings()?filter(f -> f.severity().name() == "MINOR"))?size},
                info: ${(reviewRun.findings()?filter(f -> f.severity().name() == "INFO"))?size}
            };
            
            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['严重', '重要', '次要', '信息'],
                    datasets: [{
                        data: [severityCounts.critical, severityCounts.major, severityCounts.minor, severityCounts.info],
                        backgroundColor: ['#dc2626', '#ea580c', '#f59e0b', '#3b82f6'],
                        borderRadius: 8
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                stepSize: 1
                            }
                        }
                    }
                }
            });
        }

        function initializeDimensionChart() {
            const ctx = document.getElementById('dimensionChart').getContext('2d');
            
            // Count findings by dimension
            const dimensionCounts = {
                security: ${(reviewRun.findings()?filter(f -> f.dimension().name() == "SECURITY"))?size},
                quality: ${(reviewRun.findings()?filter(f -> f.dimension().name() == "QUALITY"))?size},
                maintainability: ${(reviewRun.findings()?filter(f -> f.dimension().name() == "MAINTAINABILITY"))?size},
                performance: ${(reviewRun.findings()?filter(f -> f.dimension().name() == "PERFORMANCE"))?size},
                testCoverage: ${(reviewRun.findings()?filter(f -> f.dimension().name() == "TEST_COVERAGE"))?size}
            };
            
            new Chart(ctx, {
                type: 'pie',
                data: {
                    labels: ['安全性', '代码质量', '可维护性', '性能', '测试覆盖率'],
                    datasets: [{
                        data: [dimensionCounts.security, dimensionCounts.quality, dimensionCounts.maintainability, 
                               dimensionCounts.performance, dimensionCounts.testCoverage],
                        backgroundColor: ['#ef4444', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'bottom'
                        }
                    }
                }
            });
        }

        function filterFindings(severity) {
            const buttons = document.querySelectorAll('.filter-btn');
            const findings = document.querySelectorAll('.finding-card');
            
            buttons.forEach(btn => btn.classList.remove('active'));
            event.target.classList.add('active');
            
            findings.forEach(finding => {
                if (severity === 'all' || finding.dataset.severity === severity) {
                    finding.style.display = 'block';
                } else {
                    finding.style.display = 'none';
                }
            });
        }

        function initializeAnimations() {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add('fade-in-up');
                    }
                });
            });

            document.querySelectorAll('.dimension-card, .stat-card, .finding-card').forEach(el => {
                observer.observe(el);
            });
        }
    </script>
</body>
</html>
