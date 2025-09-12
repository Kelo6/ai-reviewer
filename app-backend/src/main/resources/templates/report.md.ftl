# AI 代码评审报告

**运行ID**: `${reviewRun.runId()}`  
**生成时间**: ${generatedAt}  
**仓库**: ${reviewRun.repo().owner()}/${reviewRun.repo().name()}  
**Pull Request**: #${reviewRun.pull().number()} - ${reviewRun.pull().title()}  

## 📊 评分概览

### 总分: ${reviewRun.scores().totalScore()?string("0.0")}/100.0

<#assign totalScore = reviewRun.scores().totalScore()>
<#if (totalScore >= 90)>
**评级**: 🟢 优秀
<#elseif (totalScore >= 70)>
**评级**: 🟡 良好  
<#elseif (totalScore >= 50)>
**评级**: 🟠 一般
<#else>
**评级**: 🔴 需改进
</#if>

### 维度得分

<#assign weights = reviewRun.scores().weights()>
| 维度 | 分数 | 权重 | 加权得分 |
|------|------|------|----------|
<#list reviewRun.scores().dimensions() as dimension, score>
<#assign weight = weights[dimension]!0.0>
| ${dimension?replace("_", " ")?capitalize} | ${score?string("0.0")} | ${(weight * 100)?string("0.0")}% | ${(score * weight)?string("0.0")} |
</#list>

## 📈 统计信息

- **文件变更数量**: ${reviewRun.stats().filesChanged()}
- **新增代码行数**: ${reviewRun.stats().linesAdded()}
- **删除代码行数**: ${reviewRun.stats().linesDeleted()}
- **处理耗时**: ${reviewRun.stats().latencyMs()}ms
<#if reviewRun.stats().tokenCostUsd()??>
- **API 消耗成本**: $${reviewRun.stats().tokenCostUsd()?string("0.00")}
</#if>

## 🔍 发现的问题

**问题总数**: ${reviewRun.findings()?size}

### 按严重性分布

<#list findingsBySeverity as severity, count>
<#if severity.name() == "CRITICAL">
- **🔥 Critical**: ${count} 个问题
<#elseif severity.name() == "MAJOR">  
- **❗ Major**: ${count} 个问题
<#elseif severity.name() == "MINOR">
- **⚠️ Minor**: ${count} 个问题  
<#elseif severity.name() == "INFO">
- **ℹ️ Info**: ${count} 个问题
</#if>
</#list>

### 按维度分布

<#list findingsByDimension as dimension, count>
- **${dimension?replace("_", " ")?capitalize}**: ${count} 个问题
</#list>

## 📝 详细问题列表

<#list reviewRun.findings() as finding>
<#if finding.severity().name() == "CRITICAL">
### 🔥 ${finding.title()}
<#elseif finding.severity().name() == "MAJOR">
### ❗ ${finding.title()}
<#elseif finding.severity().name() == "MINOR">
### ⚠️ ${finding.title()}
<#elseif finding.severity().name() == "INFO">
### ℹ️ ${finding.title()}
<#else>
### ${finding.title()}
</#if>

**文件**: `${finding.file()}`  
**行数**: ${finding.startLine()}<#if finding.endLine() != finding.startLine()>-${finding.endLine()}</#if>  
**严重性**: ${finding.severity()?replace("_", " ")?capitalize}  
**维度**: ${finding.dimension()?replace("_", " ")?capitalize}  
**置信度**: ${(finding.confidence() * 100)?string("0")}%  

**问题描述**:  
${finding.evidence()}

**建议方案**:  
${finding.suggestion()}

<#if finding.patch()??>
**建议代码修改**:  
```diff
${finding.patch()}
```
</#if>

**检测工具**: <#list finding.sources() as source>${source}<#if source_has_next>, </#if></#list>

---

</#list>

## 🔧 改进建议

<#if (totalScore >= 90)>
🎉 **代码质量优秀！** 继续保持良好的编码实践。

<#elseif (totalScore >= 70)>
👍 **代码质量良好！** 可以关注以下维度的提升：

<#list reviewRun.scores().dimensions() as dimension, score>
<#if (score < 80)>
- **${dimension?replace("_", " ")?capitalize}**: 当前得分 ${score?string("0.0")}，可以进一步优化
</#if>
</#list>

<#elseif (totalScore >= 50)>
⚠️ **代码质量一般**，建议重点关注：

<#list reviewRun.scores().dimensions() as dimension, score>
<#if (score < 70)>
- **${dimension?replace("_", " ")?capitalize}**: 当前得分 ${score?string("0.0")}，需要改进
</#if>
</#list>

<#else>
🚨 **代码质量需要改进**，请优先处理以下问题：

<#list reviewRun.findings() as finding>
<#if finding.severity().name() == "CRITICAL" || finding.severity().name() == "MAJOR">
- ${finding.file()}:${finding.startLine()} - ${finding.title()}
</#if>
</#list>

</#if>

## 📚 相关资源

- [代码评审最佳实践](https://github.com/ai-reviewer/docs)
- [安全编码指南](https://github.com/ai-reviewer/security-guidelines)
- [性能优化建议](https://github.com/ai-reviewer/performance-tips)

---

*本报告由 AI-Reviewer 自动生成 • 版本 1.0.0*
