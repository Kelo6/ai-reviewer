package com.ai.reviewer.backend.domain.orchestrator.costing;

import com.ai.reviewer.shared.model.Finding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token成本计算器，负责追踪和计算AI模型调用的Token成本。
 * 
 * <p>支持多种AI提供商的Token定价模式，包括：
 * - OpenAI GPT系列模型
 * - Anthropic Claude系列模型  
 * - Google Gemini模型
 * - 本地化模型（无成本）
 * 
 * <p>提供实时成本追踪、批量成本计算和成本预估功能。
 */
@Component
public class TokenCostCalculator {
    
    /**
     * 各种AI模型的Token定价配置（每1000个Token的价格，美元）
     */
    private static final Map<String, TokenPricing> MODEL_PRICING = createModelPricingMap();
    
    /**
     * 实时Token使用统计缓存
     */
    private final Map<String, TokenUsageStats> usageStatsCache = new ConcurrentHashMap<>();
    
    /**
     * 计算单次AI请求的Token成本。
     * 
     * @param modelName 模型名称
     * @param inputTokens 输入Token数量
     * @param outputTokens 输出Token数量
     * @return Token成本（美元）
     */
    public TokenCost calculateRequestCost(String modelName, int inputTokens, int outputTokens) {
        TokenPricing pricing = getModelPricing(modelName);
        
        double inputCost = (inputTokens / 1000.0) * pricing.inputPricePer1K();
        double outputCost = (outputTokens / 1000.0) * pricing.outputPricePer1K();
        double totalCost = inputCost + outputCost;
        
        return new TokenCost(
            modelName,
            inputTokens,
            outputTokens,
            inputTokens + outputTokens,
            BigDecimal.valueOf(totalCost).setScale(6, RoundingMode.HALF_UP).doubleValue(),
            BigDecimal.valueOf(inputCost).setScale(6, RoundingMode.HALF_UP).doubleValue(),
            BigDecimal.valueOf(outputCost).setScale(6, RoundingMode.HALF_UP).doubleValue(),
            Instant.now()
        );
    }
    
    /**
     * 计算整个Review Run的总Token成本。
     * 
     * @param modelUsages 各模型的Token使用情况
     * @return 总成本汇总
     */
    public ReviewCostSummary calculateReviewCost(List<ModelTokenUsage> modelUsages) {
        double totalCost = 0.0;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        
        Map<String, TokenCost> modelCosts = new ConcurrentHashMap<>();
        
        for (ModelTokenUsage usage : modelUsages) {
            TokenCost cost = calculateRequestCost(
                usage.modelName(), 
                usage.inputTokens(), 
                usage.outputTokens()
            );
            
            modelCosts.put(usage.modelName(), cost);
            totalCost += cost.totalCost();
            totalInputTokens += cost.inputTokens();
            totalOutputTokens += cost.outputTokens();
        }
        
        return new ReviewCostSummary(
            BigDecimal.valueOf(totalCost).setScale(4, RoundingMode.HALF_UP).doubleValue(),
            totalInputTokens,
            totalOutputTokens,
            totalInputTokens + totalOutputTokens,
            modelCosts,
            Instant.now()
        );
    }
    
    /**
     * 基于发现的问题数量估算Token成本。
     * 
     * @param findingsCount 发现的问题数量
     * @param modelsUsed 使用的模型列表
     * @param avgTokensPerFinding 每个发现平均使用的Token数量
     * @return 估算成本
     */
    public double estimateCostByFindings(int findingsCount, List<String> modelsUsed, int avgTokensPerFinding) {
        if (findingsCount == 0 || modelsUsed.isEmpty()) {
            return 0.0;
        }
        
        double totalEstimatedCost = 0.0;
        int tokensPerModel = (findingsCount * avgTokensPerFinding) / modelsUsed.size();
        
        for (String modelName : modelsUsed) {
            TokenPricing pricing = getModelPricing(modelName);
            // 假设输入输出Token比例为 2:1
            int estimatedInputTokens = (int) (tokensPerModel * 0.67);
            int estimatedOutputTokens = (int) (tokensPerModel * 0.33);
            
            double modelCost = (estimatedInputTokens / 1000.0) * pricing.inputPricePer1K() +
                              (estimatedOutputTokens / 1000.0) * pricing.outputPricePer1K();
            totalEstimatedCost += modelCost;
        }
        
        return BigDecimal.valueOf(totalEstimatedCost).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
    
    /**
     * 记录模型Token使用情况到统计缓存。
     * 
     * @param runId Review Run ID
     * @param modelName 模型名称
     * @param inputTokens 输入Token数量
     * @param outputTokens 输出Token数量
     */
    public void recordTokenUsage(String runId, String modelName, int inputTokens, int outputTokens) {
        String key = runId + ":" + modelName;
        
        usageStatsCache.compute(key, (k, existingStats) -> {
            if (existingStats == null) {
                return new TokenUsageStats(modelName, 1, inputTokens, outputTokens, Instant.now(), Instant.now());
            } else {
                return new TokenUsageStats(
                    modelName,
                    existingStats.requestCount() + 1,
                    existingStats.totalInputTokens() + inputTokens,
                    existingStats.totalOutputTokens() + outputTokens,
                    existingStats.firstRequestTime(),
                    Instant.now()
                );
            }
        });
    }
    
    /**
     * 获取指定Review Run的Token使用统计。
     * 
     * @param runId Review Run ID
     * @return Token使用统计列表
     */
    public List<TokenUsageStats> getUsageStats(String runId) {
        return usageStatsCache.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(runId + ":"))
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * 清理指定Review Run的缓存数据。
     * 
     * @param runId Review Run ID
     */
    public void clearUsageStats(String runId) {
        usageStatsCache.entrySet().removeIf(entry -> entry.getKey().startsWith(runId + ":"));
    }
    
    /**
     * 获取模型定价信息。
     */
    private TokenPricing getModelPricing(String modelName) {
        return MODEL_PRICING.getOrDefault(modelName.toLowerCase(), 
            new TokenPricing(0.001, 0.002, "Unknown Model")); // 默认定价
    }
    
    /**
     * 获取所有支持的模型定价信息。
     * 
     * @return 模型定价映射
     */
    public Map<String, TokenPricing> getAllModelPricing() {
        return Map.copyOf(MODEL_PRICING);
    }
    
    /**
     * 创建模型定价映射。
     */
    private static Map<String, TokenPricing> createModelPricingMap() {
        Map<String, TokenPricing> pricing = new HashMap<>();
        
        // OpenAI GPT-4 系列
        pricing.put("gpt-4o", new TokenPricing(0.005, 0.015, "GPT-4o"));
        pricing.put("gpt-4o-mini", new TokenPricing(0.00015, 0.0006, "GPT-4o Mini"));
        pricing.put("gpt-4-turbo", new TokenPricing(0.01, 0.03, "GPT-4 Turbo"));
        pricing.put("gpt-4", new TokenPricing(0.03, 0.06, "GPT-4"));
        pricing.put("gpt-3.5-turbo", new TokenPricing(0.0015, 0.002, "GPT-3.5 Turbo"));
        
        // Anthropic Claude 系列
        pricing.put("claude-3-5-sonnet", new TokenPricing(0.003, 0.015, "Claude 3.5 Sonnet"));
        pricing.put("claude-3-opus", new TokenPricing(0.015, 0.075, "Claude 3 Opus"));
        pricing.put("claude-3-sonnet", new TokenPricing(0.003, 0.015, "Claude 3 Sonnet"));
        pricing.put("claude-3-haiku", new TokenPricing(0.00025, 0.00125, "Claude 3 Haiku"));
        
        // Google Gemini 系列
        pricing.put("gemini-pro", new TokenPricing(0.0005, 0.0015, "Gemini Pro"));
        pricing.put("gemini-pro-vision", new TokenPricing(0.0005, 0.0015, "Gemini Pro Vision"));
        
        // 本地模型（无成本）
        pricing.put("local-llm", new TokenPricing(0.0, 0.0, "Local LLM"));
        pricing.put("ollama", new TokenPricing(0.0, 0.0, "Ollama"));
        
        return Collections.unmodifiableMap(pricing);
    }
    
    /**
     * 生成成本分析报告。
     * 
     * @param costSummary 成本汇总
     * @return 分析报告
     */
    public CostAnalysisReport generateCostAnalysis(ReviewCostSummary costSummary) {
        double avgCostPerToken = costSummary.totalTokens() > 0 ? 
            costSummary.totalCost() / costSummary.totalTokens() * 1000 : 0.0;
        
        // 找出最昂贵的模型
        String mostExpensiveModel = costSummary.modelCosts().entrySet().stream()
            .max(Map.Entry.comparingByValue((c1, c2) -> Double.compare(c1.totalCost(), c2.totalCost())))
            .map(Map.Entry::getKey)
            .orElse("N/A");
        
        // 计算成本等级
        CostLevel costLevel = determineCostLevel(costSummary.totalCost());
        
        // 生成优化建议
        List<String> optimizationSuggestions = generateOptimizationSuggestions(costSummary);
        
        return new CostAnalysisReport(
            costSummary,
            avgCostPerToken,
            mostExpensiveModel,
            costLevel,
            optimizationSuggestions
        );
    }
    
    /**
     * 确定成本等级。
     */
    private CostLevel determineCostLevel(double totalCost) {
        if (totalCost < 0.01) return CostLevel.VERY_LOW;
        if (totalCost < 0.05) return CostLevel.LOW;
        if (totalCost < 0.20) return CostLevel.MODERATE;
        if (totalCost < 1.00) return CostLevel.HIGH;
        return CostLevel.VERY_HIGH;
    }
    
    /**
     * 生成成本优化建议。
     */
    private List<String> generateOptimizationSuggestions(ReviewCostSummary costSummary) {
        List<String> suggestions = new java.util.ArrayList<>();
        
        // 检查是否使用了昂贵的模型
        boolean hasExpensiveModels = costSummary.modelCosts().entrySet().stream()
            .anyMatch(entry -> {
                TokenPricing pricing = getModelPricing(entry.getKey());
                return pricing.inputPricePer1K() > 0.01; // 超过0.01美元每1K Token
            });
        
        if (hasExpensiveModels) {
            suggestions.add("考虑在初步分析中使用更经济的模型（如GPT-4o Mini或Claude 3 Haiku）");
        }
        
        if (costSummary.totalCost() > 0.50) {
            suggestions.add("当前Review成本较高，建议优化代码分割策略以减少Token使用");
        }
        
        if (costSummary.totalInputTokens() > costSummary.totalOutputTokens() * 5) {
            suggestions.add("输入Token占比过高，考虑减少上下文信息或使用代码预处理");
        }
        
        return suggestions;
    }
    
    /**
     * Token定价信息。
     * 
     * @param inputPricePer1K 每1000个输入Token的价格（美元）
     * @param outputPricePer1K 每1000个输出Token的价格（美元）
     * @param displayName 模型显示名称
     */
    public record TokenPricing(
        double inputPricePer1K,
        double outputPricePer1K,
        String displayName
    ) {}
    
    /**
     * 单次请求的Token成本。
     */
    public record TokenCost(
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        double totalCost,
        double inputCost,
        double outputCost,
        Instant timestamp
    ) {}
    
    /**
     * 模型Token使用情况。
     */
    public record ModelTokenUsage(
        String modelName,
        int inputTokens,
        int outputTokens
    ) {}
    
    /**
     * Review成本汇总。
     */
    public record ReviewCostSummary(
        double totalCost,
        int totalInputTokens,
        int totalOutputTokens,
        int totalTokens,
        Map<String, TokenCost> modelCosts,
        Instant calculatedAt
    ) {}
    
    /**
     * Token使用统计。
     */
    public record TokenUsageStats(
        String modelName,
        int requestCount,
        int totalInputTokens,
        int totalOutputTokens,
        Instant firstRequestTime,
        Instant lastRequestTime
    ) {
        public int totalTokens() {
            return totalInputTokens + totalOutputTokens;
        }
        
        public double avgTokensPerRequest() {
            return requestCount > 0 ? (double) totalTokens() / requestCount : 0.0;
        }
    }
    
    /**
     * 成本等级枚举。
     */
    public enum CostLevel {
        VERY_LOW("很低", "green"),
        LOW("低", "lightgreen"), 
        MODERATE("适中", "yellow"),
        HIGH("高", "orange"),
        VERY_HIGH("很高", "red");
        
        private final String displayName;
        private final String color;
        
        CostLevel(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    /**
     * 成本分析报告。
     */
    public record CostAnalysisReport(
        ReviewCostSummary costSummary,
        double avgCostPerThousandTokens,
        String mostExpensiveModel,
        CostLevel costLevel,
        List<String> optimizationSuggestions
    ) {}
}
