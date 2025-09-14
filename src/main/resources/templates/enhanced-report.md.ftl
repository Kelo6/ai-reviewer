# 🤖 AI 代码评审报告

<div align="center">

![AI Reviewer](https://img.shields.io/badge/AI-Reviewer-blue?style=for-the-badge&logo=robot)
![Quality Score](https://img.shields.io/badge/Quality_Score-${reviewRun.scores().totalScore()?string("0.0")}-<#if (reviewRun.scores().totalScore() >= 90)>brightgreen<#elseif (reviewRun.scores().totalScore() >= 70)>green<#elseif (reviewRun.scores().totalScore() >= 50)>yellow<#else>red</#if>?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-<#if (reviewRun.scores().totalScore() >= 90)>优秀<#elseif (reviewRun.scores().totalScore() >= 70)>良好<#elseif (reviewRun.scores().totalScore() >= 50)>一般<#else>需改进</#if>-<#if (reviewRun.scores().totalScore() >= 90)>brightgreen<#elseif (reviewRun.scores().totalScore() >= 70)>green<#elseif (reviewRun.scores().totalScore() >= 50)>yellow<#else>red</#if>?style=for-the-badge)

</div>

---

## 📋 项目信息

| 项目属性 | 详情 |
|---------|------|
| **🏢 仓库** | `${reviewRun.repo().owner()}/${reviewRun.repo().name()}` |
| **🔗 Pull Request** | [#${reviewRun.pull().number()}](${reviewRun.pull().url()}) - ${reviewRun.pull().title()} |
| **👤 作者** | ${reviewRun.pull().author()!"-"} |
| **🕐 生成时间** | ${generatedAt} |
| **🆔 运行ID** | `${reviewRun.runId()}` |
| **🧠 AI模型** | <#list reviewRun.providerKeys() as provider>${provider}<#if provider_has_next>, </#if></#list> |

---

## 🎯 质量评分概览

<div align="center">

### 🏆 总体评分

<#assign totalScore = reviewRun.scores().totalScore()>
<div style="font-size: 4em; font-weight: bold; color: <#if (totalScore >= 90)>#10b981<#elseif (totalScore >= 70)>#3b82f6<#elseif (totalScore >= 50)>#f59e0b<#else>#ef4444</#if>;">
  ${totalScore?string("0.0")}/100
</div>

<#if (totalScore >= 90)>
### 🌟 优秀！
代码质量达到优秀标准，展现了高水平的工程实践。
<#elseif (totalScore >= 70)>
### ✅ 良好
代码质量良好，符合行业标准要求。
<#elseif (totalScore >= 50)>
### ⚠️ 一般
代码质量一般，建议进行改进。
<#else>
### 🚨 需要改进
代码质量需要重点关注和改进。
</#if>

</div>

---

## 📊 维度得分详情

<#assign weights = reviewRun.scores().weights()>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0;">

<#list reviewRun.scores().dimensions() as dimension, score>
<#assign weight = weights[dimension]!0.0>
<#assign dimensionName = dimension?replace("_", " ")?capitalize>
<#assign scoreColor = "">
<#if (score >= 90)>
  <#assign scoreColor = "#10b981">
  <#assign scoreIcon = "🟢">
<#elseif (score >= 70)>
  <#assign scoreColor = "#3b82f6">
  <#assign scoreIcon = "🔵">
<#elseif (score >= 50)>
  <#assign scoreColor = "#f59e0b">
  <#assign scoreIcon = "🟡">
<#else>
  <#assign scoreColor = "#ef4444">
  <#assign scoreIcon = "🔴">
</#if>

<div style="border: 2px solid ${scoreColor}; border-radius: 12px; padding: 16px; text-align: center; background: linear-gradient(135deg, ${scoreColor}15, ${scoreColor}05);">

### ${scoreIcon} ${dimensionName}

**${score?string("0.0")}** / 100

<div style="background: #f1f5f9; border-radius: 8px; height: 8px; margin: 10px 0;">
  <div style="background: ${scoreColor}; height: 100%; width: ${score}%; border-radius: 8px;"></div>
</div>

*权重: ${(weight * 100)?string("0.0")}%*  
*贡献: ${(score * weight)?string("0.0")}分*

</div>

</#list>

</div>

---

## 📈 统计信息

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; margin: 20px 0;">

<div style="text-align: center; padding: 15px; background: #f8fafc; border-radius: 8px; border-left: 4px solid #3b82f6;">
  <div style="font-size: 2em; font-weight: bold; color: #1e40af;">${reviewRun.stats().filesChanged()}</div>
  <div style="color: #64748b;">📁 变更文件</div>
</div>

<div style="text-align: center; padding: 15px; background: #f0fdf4; border-radius: 8px; border-left: 4px solid #10b981;">
  <div style="font-size: 2em; font-weight: bold; color: #059669;">+${reviewRun.stats().linesAdded()}</div>
  <div style="color: #64748b;">➕ 新增行数</div>
</div>

<div style="text-align: center; padding: 15px; background: #fef2f2; border-radius: 8px; border-left: 4px solid #ef4444;">
  <div style="font-size: 2em; font-weight: bold; color: #dc2626;">-${reviewRun.stats().linesDeleted()}</div>
  <div style="color: #64748b;">➖ 删除行数</div>
</div>

<div style="text-align: center; padding: 15px; background: #fffbeb; border-radius: 8px; border-left: 4px solid #f59e0b;">
  <div style="font-size: 2em; font-weight: bold; color: #d97706;">${reviewRun.stats().latencyMs()}ms</div>
  <div style="color: #64748b;">⏱️ 处理耗时</div>
</div>

<#if reviewRun.stats().tokenCostUsd()??>
<div style="text-align: center; padding: 15px; background: #f3e8ff; border-radius: 8px; border-left: 4px solid #8b5cf6;">
  <div style="font-size: 2em; font-weight: bold; color: #7c3aed;">$${reviewRun.stats().tokenCostUsd()?string("0.00")}</div>
  <div style="color: #64748b;">💰 API成本</div>
</div>
</#if>

<div style="text-align: center; padding: 15px; background: #ecfdf5; border-radius: 8px; border-left: 4px solid #10b981;">
  <div style="font-size: 2em; font-weight: bold; color: #059669;">${reviewRun.findings()?size}</div>
  <div style="color: #64748b;">🔍 发现问题</div>
</div>

</div>

---

## 🔍 问题发现汇总

<#assign criticalCount = 0>
<#assign majorCount = 0>
<#assign minorCount = 0>
<#assign infoCount = 0>

<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "CRITICAL">
    <#assign criticalCount = criticalCount + 1>
  <#elseif finding.severity().name() == "MAJOR">
    <#assign majorCount = majorCount + 1>
  <#elseif finding.severity().name() == "MINOR">
    <#assign minorCount = minorCount + 1>
  <#elseif finding.severity().name() == "INFO">
    <#assign infoCount = infoCount + 1>
  </#if>
</#list>

### 📊 按严重性分布

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0;">

<#if (criticalCount > 0)>
<div style="padding: 15px; background: linear-gradient(135deg, #fee2e2, #fecaca); border-radius: 8px; border-left: 4px solid #dc2626;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div>
      <div style="font-size: 1.5em; font-weight: bold; color: #dc2626;">🔥 Critical</div>
      <div style="color: #7f1d1d;">需要立即处理</div>
    </div>
    <div style="font-size: 2em; font-weight: bold; color: #dc2626;">${criticalCount}</div>
  </div>
</div>
</#if>

<#if (majorCount > 0)>
<div style="padding: 15px; background: linear-gradient(135deg, #fed7aa, #fdba74); border-radius: 8px; border-left: 4px solid #ea580c;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div>
      <div style="font-size: 1.5em; font-weight: bold; color: #ea580c;">❗ Major</div>
      <div style="color: #9a3412;">重要问题</div>
    </div>
    <div style="font-size: 2em; font-weight: bold; color: #ea580c;">${majorCount}</div>
  </div>
</div>
</#if>

<#if (minorCount > 0)>
<div style="padding: 15px; background: linear-gradient(135deg, #fef3c7, #fde68a); border-radius: 8px; border-left: 4px solid #f59e0b;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div>
      <div style="font-size: 1.5em; font-weight: bold; color: #d97706;">⚠️ Minor</div>
      <div style="color: #92400e;">次要问题</div>
    </div>
    <div style="font-size: 2em; font-weight: bold; color: #d97706;">${minorCount}</div>
  </div>
</div>
</#if>

<#if (infoCount > 0)>
<div style="padding: 15px; background: linear-gradient(135deg, #dbeafe, #bfdbfe); border-radius: 8px; border-left: 4px solid #3b82f6;">
  <div style="display: flex; align-items: center; justify-content: space-between;">
    <div>
      <div style="font-size: 1.5em; font-weight: bold; color: #2563eb;">ℹ️ Info</div>
      <div style="color: #1e40af;">信息提示</div>
    </div>
    <div style="font-size: 2em; font-weight: bold; color: #2563eb;">${infoCount}</div>
  </div>
</div>
</#if>

</div>

### 🎯 按维度分布

<#assign securityCount = 0>
<#assign qualityCount = 0>
<#assign maintainabilityCount = 0>
<#assign performanceCount = 0>
<#assign testCoverageCount = 0>

<#list reviewRun.findings() as finding>
  <#if finding.dimension().name() == "SECURITY">
    <#assign securityCount = securityCount + 1>
  <#elseif finding.dimension().name() == "QUALITY">
    <#assign qualityCount = qualityCount + 1>
  <#elseif finding.dimension().name() == "MAINTAINABILITY">
    <#assign maintainabilityCount = maintainabilityCount + 1>
  <#elseif finding.dimension().name() == "PERFORMANCE">
    <#assign performanceCount = performanceCount + 1>
  <#elseif finding.dimension().name() == "TEST_COVERAGE">
    <#assign testCoverageCount = testCoverageCount + 1>
  </#if>
</#list>

| 维度 | 问题数量 | 占比 |
|------|---------|------|
| 🛡️ **安全性** | ${securityCount} | ${securityCount > 0 ?string((securityCount * 100 / reviewRun.findings()?size)?string("0.0") + "%", "0%")} |
| 💎 **代码质量** | ${qualityCount} | ${qualityCount > 0 ?string((qualityCount * 100 / reviewRun.findings()?size)?string("0.0") + "%", "0%")} |
| 🔧 **可维护性** | ${maintainabilityCount} | ${maintainabilityCount > 0 ?string((maintainabilityCount * 100 / reviewRun.findings()?size)?string("0.0") + "%", "0%")} |
| ⚡ **性能** | ${performanceCount} | ${performanceCount > 0 ?string((performanceCount * 100 / reviewRun.findings()?size)?string("0.0") + "%", "0%")} |
| 🧪 **测试覆盖率** | ${testCoverageCount} | ${testCoverageCount > 0 ?string((testCoverageCount * 100 / reviewRun.findings()?size)?string("0.0") + "%", "0%")} |

---

## 📝 详细问题列表

<#if reviewRun.findings()?size == 0>

<div style="text-align: center; padding: 40px; background: #f0fdf4; border-radius: 12px; border: 2px dashed #10b981;">
  <div style="font-size: 3em;">🎉</div>
  <div style="font-size: 1.5em; font-weight: bold; color: #059669; margin: 10px 0;">太棒了！</div>
  <div style="color: #065f46;">未发现任何代码问题，代码质量很棒！</div>
</div>

<#else>

<#-- 按严重性排序显示问题 -->
<#assign sortedFindings = []>
<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "CRITICAL">
    <#assign sortedFindings = sortedFindings + [finding]>
  </#if>
</#list>
<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "MAJOR">
    <#assign sortedFindings = sortedFindings + [finding]>
  </#if>
</#list>
<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "MINOR">
    <#assign sortedFindings = sortedFindings + [finding]>
  </#if>
</#list>
<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "INFO">
    <#assign sortedFindings = sortedFindings + [finding]>
  </#if>
</#list>

<#list sortedFindings as finding>
<#assign findingIndex = finding_index + 1>

<details>
<summary>

<#if finding.severity().name() == "CRITICAL">
### 🔥 **Critical #${findingIndex}** - ${finding.title()}
<#elseif finding.severity().name() == "MAJOR">
### ❗ **Major #${findingIndex}** - ${finding.title()}
<#elseif finding.severity().name() == "MINOR">
### ⚠️ **Minor #${findingIndex}** - ${finding.title()}
<#elseif finding.severity().name() == "INFO">
### ℹ️ **Info #${findingIndex}** - ${finding.title()}
<#else>
### 📌 **${finding.severity()} #${findingIndex}** - ${finding.title()}
</#if>

</summary>

<div style="margin: 15px; padding: 20px; background: #f8fafc; border-radius: 8px; border-left: 4px solid <#if finding.severity().name() == "CRITICAL">#dc2626<#elseif finding.severity().name() == "MAJOR">#ea580c<#elseif finding.severity().name() == "MINOR">#f59e0b<#else>#3b82f6</#if>;">

#### 📍 位置信息
- **文件**: `${finding.file()}`
- **行数**: ${finding.startLine()}<#if finding.endLine() != finding.startLine()> - ${finding.endLine()}</#if>
- **维度**: ${finding.dimension()?replace("_", " ")?capitalize}
- **置信度**: ${(finding.confidence() * 100)?string("0")}%

#### 🔍 问题描述
${finding.evidence()}

#### 💡 建议方案
${finding.suggestion()}

<#if finding.patch()??>
#### 🔧 建议代码修改

```diff
${finding.patch()}
```
</#if>

#### 🏷️ 检测来源
<#list finding.sources() as source>
- `${source}`
</#list>

</div>

</details>

</#list>

</#if>

---

## 🎯 改进建议

<#if (totalScore >= 90)>

<div style="padding: 20px; background: linear-gradient(135deg, #ecfdf5, #d1fae5); border-radius: 12px; border-left: 4px solid #10b981;">

### 🌟 卓越表现！

您的代码质量已经达到了优秀标准！以下是一些保持和进一步提升的建议：

- ✅ **继续保持良好的编码实践**
- 📚 **关注新兴技术和最佳实践**
- 🔄 **定期进行代码重构和优化**
- 👥 **分享经验，帮助团队提升**

</div>

<#elseif (totalScore >= 70)>

<div style="padding: 20px; background: linear-gradient(135deg, #dbeafe, #bfdbfe); border-radius: 12px; border-left: 4px solid #3b82f6;">

### 👍 表现良好！

您的代码质量良好，以下是一些改进建议：

<#list reviewRun.scores().dimensions() as dimension, score>
  <#if (score < 80)>
- 🎯 **${dimension?replace("_", " ")?capitalize}**: 当前得分 ${score?string("0.0")}，可以进一步优化
  </#if>
</#list>

### 💪 提升建议
- 🔍 **加强代码审查**
- 📖 **完善文档和注释**
- 🧪 **增加测试覆盖率**

</div>

<#elseif (totalScore >= 50)>

<div style="padding: 20px; background: linear-gradient(135deg, #fef3c7, #fde68a); border-radius: 12px; border-left: 4px solid #f59e0b;">

### ⚠️ 有待改进

您的代码质量一般，建议重点关注以下方面：

<#list reviewRun.scores().dimensions() as dimension, score>
  <#if (score < 70)>
- ⚡ **${dimension?replace("_", " ")?capitalize}**: 当前得分 ${score?string("0.0")}，需要改进
  </#if>
</#list>

### 🚀 行动计划
1. **优先处理严重问题**
2. **制定改进时间表**
3. **加强团队培训**
4. **建立质量检查机制**

</div>

<#else>

<div style="padding: 20px; background: linear-gradient(135deg, #fee2e2, #fecaca); border-radius: 12px; border-left: 4px solid #dc2626;">

### 🚨 需要立即改进

代码质量存在严重问题，请立即采取行动：

#### 🔥 优先处理清单
<#list reviewRun.findings() as finding>
  <#if finding.severity().name() == "CRITICAL" || finding.severity().name() == "MAJOR">
- **${finding.file()}:${finding.startLine()}** - ${finding.title()}
  </#if>
</#list>

#### 📋 改进计划
1. 🛑 **暂停新功能开发**
2. 🔧 **专注质量修复**
3. 👥 **增加代码审查频率**
4. 📚 **团队培训和规范制定**

</div>

</#if>

---

## 📚 参考资源

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 15px; margin: 20px 0;">

<div style="padding: 15px; background: #f1f5f9; border-radius: 8px; border-left: 4px solid #64748b;">
  <h4>🛡️ 安全编码指南</h4>
  <p>了解安全编码最佳实践，防范常见安全漏洞</p>
  <a href="https://github.com/ai-reviewer/security-guidelines" style="color: #3b82f6;">查看指南 →</a>
</div>

<div style="padding: 15px; background: #f1f5f9; border-radius: 8px; border-left: 4px solid #64748b;">
  <h4>⚡ 性能优化技巧</h4>
  <p>学习代码性能优化的方法和工具</p>
  <a href="https://github.com/ai-reviewer/performance-tips" style="color: #3b82f6;">查看技巧 →</a>
</div>

<div style="padding: 15px; background: #f1f5f9; border-radius: 8px; border-left: 4px solid #64748b;">
  <h4>🧪 测试最佳实践</h4>
  <p>编写高质量测试代码的指导原则</p>
  <a href="https://github.com/ai-reviewer/testing-guide" style="color: #3b82f6;">查看实践 →</a>
</div>

<div style="padding: 15px; background: #f1f5f9; border-radius: 8px; border-left: 4px solid #64748b;">
  <h4>📖 代码审查指南</h4>
  <p>有效进行代码审查的方法和技巧</p>
  <a href="https://github.com/ai-reviewer/review-guide" style="color: #3b82f6;">查看指南 →</a>
</div>

</div>

---

<div style="text-align: center; padding: 30px; background: linear-gradient(135deg, #f8fafc, #e2e8f0); border-radius: 12px; margin: 30px 0;">

### 🤖 关于 AI-Reviewer

AI-Reviewer 是一个基于人工智能的代码审查工具，旨在帮助开发团队提升代码质量和安全性。

**版本**: 1.0.0 | **生成时间**: ${generatedAt}

---

*💡 如有问题或建议，欢迎通过 [GitHub Issues](https://github.com/ai-reviewer/ai-reviewer/issues) 联系我们*

</div>
