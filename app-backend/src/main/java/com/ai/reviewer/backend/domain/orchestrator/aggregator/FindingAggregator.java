package com.ai.reviewer.backend.domain.orchestrator.aggregator;

import com.ai.reviewer.shared.model.Finding;
import com.ai.reviewer.shared.enums.Severity;
import com.ai.reviewer.shared.enums.Dimension;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregator for combining and deduplicating findings from multiple sources.
 * 
 * <p>This component handles merging findings from static analyzers and AI reviewers,
 * removing duplicates, and ranking findings by importance and confidence.
 */
@Component
public class FindingAggregator {
    
    // Similarity thresholds for deduplication
    private static final double MESSAGE_SIMILARITY_THRESHOLD = 0.8;
    private static final int LOCATION_PROXIMITY_THRESHOLD = 3; // lines
    
    /**
     * Aggregate findings from multiple sources with deduplication.
     * 
     * @param staticFindings findings from static analysis tools
     * @param aiFindings findings from AI reviewers
     * @param config aggregation configuration
     * @return aggregated and deduplicated findings
     */
    public AggregationResult aggregate(List<Finding> staticFindings, List<Finding> aiFindings,
                                     AggregationConfig config) {
        
        // Step 1: Combine all findings with source tracking
        List<SourcedFinding> allFindings = new ArrayList<>();
        staticFindings.forEach(f -> allFindings.add(new SourcedFinding(f, FindingSource.STATIC)));
        aiFindings.forEach(f -> allFindings.add(new SourcedFinding(f, FindingSource.AI)));
        
        // Step 2: Group similar findings
        List<FindingGroup> groups = groupSimilarFindings(allFindings, config);
        
        // Step 3: Merge findings within each group
        List<Finding> mergedFindings = groups.stream()
            .map(group -> mergeFindingsInGroup(group, config))
            .collect(Collectors.toList());
        
        // Step 4: Rank and filter findings
        List<Finding> rankedFindings = rankAndFilter(mergedFindings, config);
        
        // Step 5: Generate aggregation statistics
        AggregationStats stats = generateStats(groups, mergedFindings, rankedFindings);
        
        return new AggregationResult(rankedFindings, stats, groups);
    }
    
    /**
     * Group similar findings together for potential merging.
     */
    private List<FindingGroup> groupSimilarFindings(List<SourcedFinding> findings, AggregationConfig config) {
        List<FindingGroup> groups = new ArrayList<>();
        List<SourcedFinding> remaining = new ArrayList<>(findings);
        
        while (!remaining.isEmpty()) {
            SourcedFinding current = remaining.remove(0);
            FindingGroup group = new FindingGroup(current);
            
            // Find similar findings
            Iterator<SourcedFinding> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                SourcedFinding candidate = iterator.next();
                
                if (areSimilar(current.finding(), candidate.finding(), config)) {
                    group.addFinding(candidate);
                    iterator.remove();
                }
            }
            
            groups.add(group);
        }
        
        return groups;
    }
    
    /**
     * Check if two findings are similar enough to be merged.
     */
    private boolean areSimilar(Finding f1, Finding f2, AggregationConfig config) {
        // Must be same file
        if (!Objects.equals(f1.file(), f2.file())) {
            return false;
        }
        
        // Check location proximity
        if (!areLocationsClose(f1, f2, config.locationProximityThreshold())) {
            return false;
        }
        
        // Check message similarity
        if (calculateMessageSimilarity(f1.message(), f2.message()) < config.messageSimilarityThreshold()) {
            return false;
        }
        
        // Check dimension compatibility
        return f1.dimension() == f2.dimension();
    }
    
    /**
     * Check if two findings are at nearby locations.
     */
    private boolean areLocationsClose(Finding f1, Finding f2, int threshold) {
        if (f1.line() == null || f2.line() == null) {
            return true; // File-level findings
        }
        
        return Math.abs(f1.line() - f2.line()) <= threshold;
    }
    
    /**
     * Calculate similarity between two messages using simple string metrics.
     */
    private double calculateMessageSimilarity(String msg1, String msg2) {
        if (msg1 == null || msg2 == null) {
            return msg1 == msg2 ? 1.0 : 0.0;
        }
        
        // Normalize messages for comparison
        String norm1 = normalizeMessage(msg1);
        String norm2 = normalizeMessage(msg2);
        
        // Use Jaccard similarity on words
        Set<String> words1 = new HashSet<>(Arrays.asList(norm1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(norm2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Normalize message for comparison.
     */
    private String normalizeMessage(String message) {
        return message.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
    
    /**
     * Merge findings within a group into a single finding.
     */
    private Finding mergeFindingsInGroup(FindingGroup group, AggregationConfig config) {
        if (group.findings().size() == 1) {
            return group.findings().get(0).finding();
        }
        
        // Select the primary finding (highest confidence or from preferred source)
        SourcedFinding primary = selectPrimaryFinding(group.findings(), config);
        Finding primaryFinding = primary.finding();
        
        // Collect additional sources and evidence
        List<String> allSources = group.findings().stream()
            .flatMap(sf -> sf.finding().sources().stream())
            .distinct()
            .collect(Collectors.toList());
        
        // Calculate merged confidence
        double mergedConfidence = calculateMergedConfidence(group.findings(), config);
        
        // Create merged message if multiple findings
        String mergedMessage = createMergedMessage(group.findings(), config);
        
        // Determine merged severity (take the highest)
        Severity mergedSeverity = group.findings().stream()
            .map(sf -> sf.finding().severity())
            .max(Comparator.comparing(Severity::ordinal))
            .orElse(primaryFinding.severity());
        
        return new Finding(
            primaryFinding.id(),
            primaryFinding.file(),
            primaryFinding.startLine(),
            primaryFinding.endLine(),
            mergedSeverity,
            primaryFinding.dimension(),
            mergedMessage,
            primaryFinding.evidence(),
            primaryFinding.suggestion(),
            primaryFinding.patch(),
            allSources,
            mergedConfidence
        );
    }
    
    /**
     * Select the primary finding from a group.
     */
    private SourcedFinding selectPrimaryFinding(List<SourcedFinding> findings, AggregationConfig config) {
        return findings.stream()
            .max(Comparator
                .comparing((SourcedFinding sf) -> getSourcePriority(sf.source(), config))
                .thenComparing(sf -> sf.finding().confidence())
                .thenComparing(sf -> sf.finding().severity().ordinal()))
            .orElse(findings.get(0));
    }
    
    /**
     * Get source priority for ranking.
     */
    private int getSourcePriority(FindingSource source, AggregationConfig config) {
        return switch (source) {
            case STATIC -> config.staticAnalysisPriority();
            case AI -> config.aiReviewPriority();
        };
    }
    
    /**
     * Calculate merged confidence from multiple findings.
     */
    private double calculateMergedConfidence(List<SourcedFinding> findings, AggregationConfig config) {
        if (findings.size() == 1) {
            return findings.get(0).finding().confidence();
        }
        
        // Use weighted average based on source confidence
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        for (SourcedFinding sf : findings) {
            double weight = getSourceWeight(sf.source(), config);
            weightedSum += sf.finding().confidence() * weight;
            totalWeight += weight;
        }
        
        double baseConfidence = totalWeight > 0 ? weightedSum / totalWeight : 0.5;
        
        // Boost confidence when multiple sources agree
        double consensusBoost = Math.min(0.3, (findings.size() - 1) * 0.1);
        
        return Math.min(1.0, baseConfidence + consensusBoost);
    }
    
    /**
     * Get source weight for confidence calculation.
     */
    private double getSourceWeight(FindingSource source, AggregationConfig config) {
        return switch (source) {
            case STATIC -> config.staticAnalysisWeight();
            case AI -> config.aiReviewWeight();
        };
    }
    
    /**
     * Create merged message from multiple findings.
     */
    private String createMergedMessage(List<SourcedFinding> findings, AggregationConfig config) {
        if (findings.size() == 1) {
            return findings.get(0).finding().message();
        }
        
        // Use the primary finding's message as base
        SourcedFinding primary = selectPrimaryFinding(findings, config);
        String primaryMessage = primary.finding().message();
        
        // Add additional insights from other sources
        List<String> additionalInsights = findings.stream()
            .filter(sf -> sf != primary)
            .map(sf -> sf.finding().message())
            .distinct()
            .collect(Collectors.toList());
        
        if (additionalInsights.isEmpty()) {
            return primaryMessage;
        }
        
        StringBuilder merged = new StringBuilder(primaryMessage);
        
        if (config.includeAdditionalInsights()) {
            merged.append("\n\nAdditional insights:");
            for (String insight : additionalInsights) {
                merged.append("\n- ").append(insight);
            }
        }
        
        return merged.toString();
    }
    
    /**
     * Rank and filter findings based on importance.
     */
    private List<Finding> rankAndFilter(List<Finding> findings, AggregationConfig config) {
        return findings.stream()
            .filter(f -> f.confidence() >= config.minConfidenceThreshold())
            .filter(f -> isRelevantSeverity(f.severity(), config.minSeverity()))
            .sorted(Comparator
                .comparing((Finding f) -> f.severity().ordinal()).reversed()
                .thenComparing((Finding f) -> f.confidence()).reversed()
                .thenComparing(f -> f.dimension().ordinal()))
            .limit(config.maxFindings())
            .collect(Collectors.toList());
    }
    
    /**
     * Check if severity meets minimum threshold.
     */
    private boolean isRelevantSeverity(Severity severity, Severity minSeverity) {
        return severity.ordinal() >= minSeverity.ordinal();
    }
    
    /**
     * Generate aggregation statistics.
     */
    private AggregationStats generateStats(List<FindingGroup> groups, 
                                         List<Finding> merged, 
                                         List<Finding> filtered) {
        int totalFindings = groups.stream().mapToInt(g -> g.findings().size()).sum();
        int duplicatesRemoved = totalFindings - merged.size();
        int filteredOut = merged.size() - filtered.size();
        
        Map<Dimension, Integer> findingsByDimension = filtered.stream()
            .collect(Collectors.groupingBy(Finding::dimension, Collectors.summingInt(f -> 1)));
        
        Map<Severity, Integer> findingsBySeverity = filtered.stream()
            .collect(Collectors.groupingBy(Finding::severity, Collectors.summingInt(f -> 1)));
        
        return new AggregationStats(
            totalFindings,
            duplicatesRemoved,
            filteredOut,
            filtered.size(),
            findingsByDimension,
            findingsBySeverity,
            calculateAverageConfidence(filtered)
        );
    }
    
    /**
     * Calculate average confidence of findings.
     */
    private double calculateAverageConfidence(List<Finding> findings) {
        return findings.stream()
            .mapToDouble(Finding::confidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Finding with source information.
     */
    private record SourcedFinding(Finding finding, FindingSource source) {}
    
    /**
     * Group of similar findings.
     */
    private static class FindingGroup {
        private final List<SourcedFinding> findings = new ArrayList<>();
        
        public FindingGroup(SourcedFinding initial) {
            findings.add(initial);
        }
        
        public void addFinding(SourcedFinding finding) {
            findings.add(finding);
        }
        
        public List<SourcedFinding> findings() {
            return findings;
        }
        
        public int size() {
            return findings.size();
        }
    }
    
    /**
     * Source of a finding.
     */
    public enum FindingSource {
        STATIC, AI
    }
    
    /**
     * Aggregation configuration.
     */
    public record AggregationConfig(
        double messageSimilarityThreshold,
        int locationProximityThreshold,
        double minConfidenceThreshold,
        Severity minSeverity,
        int maxFindings,
        boolean includeAdditionalInsights,
        int staticAnalysisPriority,
        int aiReviewPriority,
        double staticAnalysisWeight,
        double aiReviewWeight
    ) {
        public static AggregationConfig defaultConfig() {
            return new AggregationConfig(
                0.8,    // messageSimilarityThreshold
                3,      // locationProximityThreshold
                0.3,    // minConfidenceThreshold
                Severity.INFO,  // minSeverity
                100,    // maxFindings
                true,   // includeAdditionalInsights
                2,      // staticAnalysisPriority
                1,      // aiReviewPriority
                1.0,    // staticAnalysisWeight
                0.8     // aiReviewWeight
            );
        }
        
        public static AggregationConfig strict() {
            return new AggregationConfig(
                0.9,    // messageSimilarityThreshold
                2,      // locationProximityThreshold
                0.7,    // minConfidenceThreshold
                Severity.MINOR, // minSeverity
                50,     // maxFindings
                false,  // includeAdditionalInsights
                1,      // staticAnalysisPriority
                2,      // aiReviewPriority
                0.8,    // staticAnalysisWeight
                1.0     // aiReviewWeight
            );
        }
        
        public static AggregationConfig permissive() {
            return new AggregationConfig(
                0.6,    // messageSimilarityThreshold
                5,      // locationProximityThreshold
                0.1,    // minConfidenceThreshold
                Severity.INFO,  // minSeverity
                200,    // maxFindings
                true,   // includeAdditionalInsights
                1,      // staticAnalysisPriority
                1,      // aiReviewPriority
                1.0,    // staticAnalysisWeight
                1.0     // aiReviewWeight
            );
        }
    }
    
    /**
     * Result of finding aggregation.
     */
    public record AggregationResult(
        List<Finding> findings,
        AggregationStats stats,
        List<FindingGroup> groups
    ) {}
    
    /**
     * Statistics about the aggregation process.
     */
    public record AggregationStats(
        int totalInputFindings,
        int duplicatesRemoved,
        int filteredOut,
        int finalFindingsCount,
        Map<Dimension, Integer> findingsByDimension,
        Map<Severity, Integer> findingsBySeverity,
        double averageConfidence
    ) {
        public double getDeduplicationRate() {
            return totalInputFindings > 0 ? 
                (double) duplicatesRemoved / totalInputFindings * 100 : 0.0;
        }
        
        public double getFilterRate() {
            return (totalInputFindings - duplicatesRemoved) > 0 ? 
                (double) filteredOut / (totalInputFindings - duplicatesRemoved) * 100 : 0.0;
        }
    }
}
