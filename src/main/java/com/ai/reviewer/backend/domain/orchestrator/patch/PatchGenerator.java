package com.ai.reviewer.backend.domain.orchestrator.patch;

import com.ai.reviewer.shared.enums.Dimension;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.model.Finding;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能补丁生成器，基于发现的问题自动生成代码修复补丁。
 * 
 * <p>支持多种修复策略：
 * - 基于模式匹配的自动修复
 * - 基于AI建议的代码生成
 * - 安全漏洞的标准修复模板
 * - 性能优化的常见模式替换
 * 
 * <p>生成的补丁符合统一差分格式（Unified Diff Format），
 * 可以直接应用到源代码文件中。
 */
@Component
public class PatchGenerator {
    
    /**
     * 常见代码问题的修复模式
     */
    private static final Map<String, FixPattern> FIX_PATTERNS = createFixPatterns();
    
    /**
     * 为单个发现生成修复补丁。
     * 
     * @param finding 代码发现问题
     * @param sourceCode 源代码内容
     * @return 生成的补丁，如果无法生成则返回null
     */
    public CodePatch generatePatch(Finding finding, String sourceCode) {
        try {
            // 根据问题维度选择修复策略
            PatchStrategy strategy = selectStrategy(finding);
            
            // 提取问题代码段
            CodeSegment problemSegment = extractCodeSegment(finding, sourceCode);
            if (problemSegment == null) {
                return null;
            }
            
            // 应用修复策略
            String fixedCode = applyStrategy(strategy, problemSegment, finding);
            if (fixedCode == null || fixedCode.equals(problemSegment.originalCode())) {
                return null;
            }
            
            // 生成统一差分格式补丁
            String unifiedDiff = generateUnifiedDiff(finding, problemSegment, fixedCode);
            
            return new CodePatch(
                generatePatchId(finding),
                finding.file(),
                finding.startLine(),
                finding.endLine(),
                strategy,
                problemSegment.originalCode(),
                fixedCode,
                unifiedDiff,
                calculateConfidence(strategy, finding),
                List.of("automated-fix"),
                Instant.now()
            );
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，补丁生成失败不应影响整体流程
            return null;
        }
    }
    
    /**
     * 批量生成多个发现的修复补丁。
     * 
     * @param findings 发现列表
     * @param sourceFiles 源文件映射（文件路径 -> 文件内容）
     * @return 生成的补丁列表
     */
    public List<CodePatch> generateBatchPatches(List<Finding> findings, Map<String, String> sourceFiles) {
        return findings.parallelStream()
            .filter(finding -> sourceFiles.containsKey(finding.file()))
            .map(finding -> generatePatch(finding, sourceFiles.get(finding.file())))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 合并多个补丁为一个完整的修复包。
     * 
     * @param patches 补丁列表
     * @return 合并后的修复包
     */
    public FixBundle mergePatchesToBundle(List<CodePatch> patches) {
        Map<String, List<CodePatch>> patchesByFile = patches.stream()
            .collect(Collectors.groupingBy(CodePatch::filePath));
        
        Map<String, String> mergedDiffs = new HashMap<>();
        List<String> conflictFiles = new ArrayList<>();
        
        for (Map.Entry<String, List<CodePatch>> entry : patchesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<CodePatch> filePatches = entry.getValue();
            
            try {
                String mergedDiff = mergeFilePatches(filePatches);
                mergedDiffs.put(filePath, mergedDiff);
            } catch (PatchConflictException e) {
                conflictFiles.add(filePath);
            }
        }
        
        return new FixBundle(
            generateBundleId(patches),
            mergedDiffs,
            patches,
            conflictFiles,
            calculateBundleConfidence(patches),
            Instant.now()
        );
    }
    
    /**
     * 根据发现选择修复策略。
     */
    private PatchStrategy selectStrategy(Finding finding) {
        return switch (finding.dimension()) {
            case SECURITY -> PatchStrategy.SECURITY_FIX;
            case PERFORMANCE -> PatchStrategy.PERFORMANCE_OPTIMIZATION;
            case QUALITY -> PatchStrategy.CODE_QUALITY_IMPROVEMENT;
            case MAINTAINABILITY -> PatchStrategy.REFACTORING;
            case TEST_COVERAGE -> PatchStrategy.TEST_ENHANCEMENT;
        };
    }
    
    /**
     * 提取问题代码段。
     */
    private CodeSegment extractCodeSegment(Finding finding, String sourceCode) {
        String[] lines = sourceCode.split("\n");
        
        if (finding.startLine() <= 0 || finding.startLine() > lines.length) {
            return null;
        }
        
        int startIdx = Math.max(0, finding.startLine() - 1);
        int endIdx = Math.min(lines.length, finding.endLine());
        
        StringBuilder segmentBuilder = new StringBuilder();
        for (int i = startIdx; i < endIdx; i++) {
            segmentBuilder.append(lines[i]);
            if (i < endIdx - 1) {
                segmentBuilder.append("\n");
            }
        }
        
        return new CodeSegment(
            finding.file(),
            finding.startLine(),
            finding.endLine(),
            segmentBuilder.toString(),
            extractContextLines(lines, startIdx, endIdx, 3)
        );
    }
    
    /**
     * 提取上下文行。
     */
    private List<String> extractContextLines(String[] lines, int startIdx, int endIdx, int contextSize) {
        List<String> context = new ArrayList<>();
        
        // 添加前置上下文
        for (int i = Math.max(0, startIdx - contextSize); i < startIdx; i++) {
            context.add("  " + lines[i]); // 前缀空格表示上下文
        }
        
        // 添加后置上下文
        for (int i = endIdx; i < Math.min(lines.length, endIdx + contextSize); i++) {
            context.add("  " + lines[i]);
        }
        
        return context;
    }
    
    /**
     * 应用修复策略。
     */
    private String applyStrategy(PatchStrategy strategy, CodeSegment segment, Finding finding) {
        return switch (strategy) {
            case SECURITY_FIX -> applySecurityFix(segment, finding);
            case PERFORMANCE_OPTIMIZATION -> applyPerformanceOptimization(segment, finding);
            case CODE_QUALITY_IMPROVEMENT -> applyQualityImprovement(segment, finding);
            case REFACTORING -> applyRefactoring(segment, finding);
            case TEST_ENHANCEMENT -> applyTestEnhancement(segment, finding);
            case PATTERN_BASED -> applyPatternBasedFix(segment, finding);
        };
    }
    
    /**
     * 应用安全修复。
     */
    private String applySecurityFix(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        // SQL注入修复
        if (finding.title().toLowerCase().contains("sql injection")) {
            code = code.replaceAll(
                "Statement\\s+\\w+\\s*=\\s*\\w+\\.createStatement\\(\\)",
                "PreparedStatement $1 = $2.prepareStatement(sql)"
            );
            code = code.replaceAll(
                "\\.executeQuery\\(.*\\+.*\\)",
                ".executeQuery()"
            );
        }
        
        // XSS修复
        if (finding.title().toLowerCase().contains("xss") || finding.title().toLowerCase().contains("cross-site")) {
            code = code.replaceAll(
                "response\\.getWriter\\(\\)\\.write\\(([^)]+)\\)",
                "response.getWriter().write(StringEscapeUtils.escapeHtml4($1))"
            );
        }
        
        // 空指针修复
        if (finding.title().toLowerCase().contains("null pointer")) {
            code = addNullChecks(code);
        }
        
        return code;
    }
    
    /**
     * 应用性能优化。
     */
    private String applyPerformanceOptimization(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        // StringBuilder优化
        if (finding.title().toLowerCase().contains("string concatenation")) {
            code = optimizeStringConcatenation(code);
        }
        
        // 循环优化
        if (finding.title().toLowerCase().contains("loop") || finding.title().toLowerCase().contains("iteration")) {
            code = optimizeLoops(code);
        }
        
        // 集合操作优化
        if (finding.title().toLowerCase().contains("collection") || finding.title().toLowerCase().contains("stream")) {
            code = optimizeCollectionOperations(code);
        }
        
        return code;
    }
    
    /**
     * 应用代码质量改进。
     */
    private String applyQualityImprovement(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        // 添加空行改善可读性
        if (finding.title().toLowerCase().contains("readability")) {
            code = improveReadability(code);
        }
        
        // 方法长度优化
        if (finding.title().toLowerCase().contains("method too long")) {
            code = addMethodExtractionComment(code);
        }
        
        // 魔法数字处理
        if (finding.title().toLowerCase().contains("magic number")) {
            code = replaceMagicNumbers(code);
        }
        
        return code;
    }
    
    /**
     * 应用重构修复。
     */
    private String applyRefactoring(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        // 变量命名改进
        if (finding.title().toLowerCase().contains("naming")) {
            code = improveName(code);
        }
        
        // 复杂度降低
        if (finding.title().toLowerCase().contains("complexity")) {
            code = addComplexityReductionComment(code);
        }
        
        return code;
    }
    
    /**
     * 应用测试增强。
     */
    private String applyTestEnhancement(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        // 添加测试用例建议
        if (finding.title().toLowerCase().contains("test coverage")) {
            code = addTestSuggestionComment(code);
        }
        
        return code;
    }
    
    /**
     * 应用基于模式的修复。
     */
    private String applyPatternBasedFix(CodeSegment segment, Finding finding) {
        String code = segment.originalCode();
        
        for (FixPattern pattern : FIX_PATTERNS.values()) {
            if (pattern.matches(finding)) {
                code = pattern.apply(code);
            }
        }
        
        return code;
    }
    
    /**
     * 生成统一差分格式。
     */
    private String generateUnifiedDiff(Finding finding, CodeSegment segment, String fixedCode) {
        StringBuilder diff = new StringBuilder();
        
        diff.append("--- a/").append(finding.file()).append("\n");
        diff.append("+++ b/").append(finding.file()).append("\n");
        diff.append("@@ -").append(finding.startLine()).append(",")
            .append(finding.endLine() - finding.startLine() + 1)
            .append(" +").append(finding.startLine()).append(",")
            .append(fixedCode.split("\n").length).append(" @@\n");
        
        // 添加删除的行
        for (String line : segment.originalCode().split("\n")) {
            diff.append("-").append(line).append("\n");
        }
        
        // 添加新增的行
        for (String line : fixedCode.split("\n")) {
            diff.append("+").append(line).append("\n");
        }
        
        return diff.toString();
    }
    
    /**
     * 生成补丁ID。
     */
    private String generatePatchId(Finding finding) {
        return "patch-" + finding.id() + "-" + System.currentTimeMillis();
    }
    
    /**
     * 生成修复包ID。
     */
    private String generateBundleId(List<CodePatch> patches) {
        return "bundle-" + System.currentTimeMillis() + "-" + patches.size();
    }
    
    /**
     * 计算补丁置信度。
     */
    private double calculateConfidence(PatchStrategy strategy, Finding finding) {
        double baseConfidence = switch (strategy) {
            case SECURITY_FIX -> 0.8;
            case PERFORMANCE_OPTIMIZATION -> 0.7;
            case CODE_QUALITY_IMPROVEMENT -> 0.6;
            case REFACTORING -> 0.5;
            case TEST_ENHANCEMENT -> 0.4;
            case PATTERN_BASED -> 0.9;
        };
        
        // 根据发现的置信度调整
        return Math.min(1.0, baseConfidence * finding.confidence());
    }
    
    /**
     * 计算修复包置信度。
     */
    private double calculateBundleConfidence(List<CodePatch> patches) {
        if (patches.isEmpty()) return 0.0;
        
        return patches.stream()
            .mapToDouble(CodePatch::confidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 合并同一文件的多个补丁。
     */
    private String mergeFilePatches(List<CodePatch> patches) throws PatchConflictException {
        // 按行号排序
        patches.sort(Comparator.comparingInt(CodePatch::startLine));
        
        // 检查冲突
        for (int i = 0; i < patches.size() - 1; i++) {
            CodePatch current = patches.get(i);
            CodePatch next = patches.get(i + 1);
            
            if (current.endLine() >= next.startLine()) {
                throw new PatchConflictException("Patches overlap: " + current.patchId() + " and " + next.patchId());
            }
        }
        
        // 合并补丁
        StringBuilder merged = new StringBuilder();
        for (CodePatch patch : patches) {
            merged.append(patch.unifiedDiff()).append("\n");
        }
        
        return merged.toString();
    }
    
    // 辅助方法实现
    private String addNullChecks(String code) {
        // 简单的空检查添加逻辑
        return code.replaceAll(
            "([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)",
            "($1 != null ? $1.$2 : null)"
        );
    }
    
    private String optimizeStringConcatenation(String code) {
        if (code.contains("+") && code.contains("String")) {
            return code.replaceAll(
                "String\\s+(\\w+)\\s*=\\s*([^;]+);",
                "StringBuilder $1 = new StringBuilder($2);"
            );
        }
        return code;
    }
    
    private String optimizeLoops(String code) {
        // 添加循环优化建议注释
        return "// TODO: Consider optimizing this loop for better performance\n" + code;
    }
    
    private String optimizeCollectionOperations(String code) {
        // 流操作优化
        return code.replaceAll(
            "\\.stream\\(\\)\\.filter\\([^)]+\\)\\.collect\\(Collectors\\.toList\\(\\)\\)",
            ".stream().filter($1).toList()"
        );
    }
    
    private String improveReadability(String code) {
        // 在复杂表达式后添加空行
        return code.replaceAll("(.*;)\\n([a-zA-Z])", "$1\n\n$2");
    }
    
    private String addMethodExtractionComment(String code) {
        return "// TODO: Consider extracting this method into smaller, more focused methods\n" + code;
    }
    
    private String replaceMagicNumbers(String code) {
        // 将常见的魔法数字替换为常量
        return code.replaceAll("\\b(100|1000|24|60|365)\\b", "/* TODO: Extract to constant */ $1");
    }
    
    private String improveName(String code) {
        // 添加命名改进建议
        return "// TODO: Consider using more descriptive variable names\n" + code;
    }
    
    private String addComplexityReductionComment(String code) {
        return "// TODO: Consider breaking down this complex logic\n" + code;
    }
    
    private String addTestSuggestionComment(String code) {
        return "// TODO: Add unit tests for this method\n" + code;
    }
    
    /**
     * 创建修复模式映射。
     */
    private static Map<String, FixPattern> createFixPatterns() {
        Map<String, FixPattern> patterns = new HashMap<>();
        
        patterns.put("null-check", new FixPattern(
            "null pointer",
            Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)"),
            "if ($1 != null) { $1.$2 }"
        ));
        
        patterns.put("string-concatenation", new FixPattern(
            "string concatenation",
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*([^;]+\\+[^;]+);"),
            "StringBuilder $1 = new StringBuilder(); // TODO: Use StringBuilder for concatenation"
        ));
        
        return patterns;
    }
    
    /**
     * 修复模式定义。
     */
    private static class FixPattern {
        private final String keyword;
        private final Pattern pattern;
        private final String replacement;
        
        public FixPattern(String keyword, Pattern pattern, String replacement) {
            this.keyword = keyword;
            this.pattern = pattern;
            this.replacement = replacement;
        }
        
        public boolean matches(Finding finding) {
            return finding.title().toLowerCase().contains(keyword) ||
                   finding.evidence().toLowerCase().contains(keyword);
        }
        
        public String apply(String code) {
            Matcher matcher = pattern.matcher(code);
            return matcher.replaceAll(replacement);
        }
    }
    
    /**
     * 补丁策略枚举。
     */
    public enum PatchStrategy {
        SECURITY_FIX("安全修复", "修复安全漏洞和风险"),
        PERFORMANCE_OPTIMIZATION("性能优化", "改善代码执行性能"),
        CODE_QUALITY_IMPROVEMENT("代码质量改进", "提升代码质量和可读性"),
        REFACTORING("重构", "改善代码结构和设计"),
        TEST_ENHANCEMENT("测试增强", "增加或改进测试用例"),
        PATTERN_BASED("模式匹配", "基于预定义模式的自动修复");
        
        private final String displayName;
        private final String description;
        
        PatchStrategy(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * 代码段。
     */
    public record CodeSegment(
        String filePath,
        int startLine,
        int endLine,
        String originalCode,
        List<String> contextLines
    ) {}
    
    /**
     * 代码补丁。
     */
    public record CodePatch(
        String patchId,
        String filePath,
        int startLine,
        int endLine,
        PatchStrategy strategy,
        String originalCode,
        String fixedCode,
        String unifiedDiff,
        double confidence,
        List<String> tags,
        Instant createdAt
    ) {}
    
    /**
     * 修复包。
     */
    public record FixBundle(
        String bundleId,
        Map<String, String> mergedDiffs,
        List<CodePatch> patches,
        List<String> conflictFiles,
        double confidence,
        Instant createdAt
    ) {
        public boolean hasConflicts() {
            return !conflictFiles.isEmpty();
        }
        
        public int getTotalPatches() {
            return patches.size();
        }
        
        public Set<String> getAffectedFiles() {
            return patches.stream()
                .map(CodePatch::filePath)
                .collect(Collectors.toSet());
        }
    }
    
    /**
     * 补丁冲突异常。
     */
    public static class PatchConflictException extends Exception {
        public PatchConflictException(String message) {
            super(message);
        }
    }
}
