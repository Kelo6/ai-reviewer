package com.ai.reviewer.backend.domain.orchestrator.splitter;

import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer;
import com.ai.reviewer.backend.domain.orchestrator.analyzer.StaticAnalyzer.CodeSegment;
import com.ai.reviewer.shared.model.DiffHunk;
import com.ai.reviewer.shared.enums.FileStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code splitter that divides diff hunks into analyzable segments.
 * 
 * <p>Supports multiple splitting strategies:
 * - Function/method based splitting
 * - Class/interface based splitting  
 * - Fixed line window splitting
 * - Intelligent context-aware splitting
 */
@Component
public class CodeSplitter {
    
    // Language-specific patterns for function/method detection
    private static final Map<String, Pattern> FUNCTION_PATTERNS = Map.of(
        "java", Pattern.compile(
            "(?:public|private|protected|static|final|abstract|synchronized)?\\s*" +
            "(?:[\\w<>\\[\\]\\s,]+\\s+)?" +  // return type
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(" // method name
        ),
        "javascript", Pattern.compile(
            "(?:function\\s+([a-zA-Z_][a-zA-Z0-9_]*)|([a-zA-Z_][a-zA-Z0-9_]*)\\s*[=:]\\s*function|([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\()"
        ),
        "python", Pattern.compile(
            "def\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
        ),
        "cpp", Pattern.compile(
            "(?:[\\w:*&\\s]+\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
        )
    );
    
    // Class/interface patterns
    private static final Map<String, Pattern> CLASS_PATTERNS = Map.of(
        "java", Pattern.compile(
            "(?:public|private|protected)?\\s*(?:static)?\\s*(?:final)?\\s*(?:abstract)?\\s*" +
            "(class|interface|enum)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
        ),
        "javascript", Pattern.compile(
            "class\\s+([a-zA-Z_][a-zA-Z0-9_]*)|function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
        ),
        "python", Pattern.compile(
            "class\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
        ),
        "cpp", Pattern.compile(
            "(class|struct)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
        )
    );
    
    /**
     * Split diff hunks into code segments based on strategy.
     * 
     * @param diffHunks list of diff hunks to split
     * @param strategy splitting strategy to use
     * @return list of code segments
     */
    public List<CodeSegment> split(List<DiffHunk> diffHunks, SplittingStrategy strategy) {
        List<CodeSegment> segments = new ArrayList<>();
        
        for (DiffHunk hunk : diffHunks) {
            // Skip deleted files
            if (hunk.status() == FileStatus.DELETED) {
                continue;
            }
            
            // Extract changed content from patch
            String content = extractContentFromPatch(hunk.patch());
            if (content.isEmpty()) {
                continue;
            }
            
            String language = detectLanguage(hunk.file());
            
            switch (strategy.type()) {
                case FUNCTION -> segments.addAll(splitByFunction(hunk.file(), content, language, strategy));
                case CLASS -> segments.addAll(splitByClass(hunk.file(), content, language, strategy));
                case LINES -> segments.addAll(splitByLines(hunk.file(), content, language, strategy));
                case INTELLIGENT -> segments.addAll(splitIntelligently(hunk.file(), content, language, strategy));
                case FILE -> segments.add(createFileSegment(hunk.file(), content, language));
            }
        }
        
        return segments;
    }
    
    /**
     * Split content by functions/methods.
     */
    private List<CodeSegment> splitByFunction(String filePath, String content, String language, 
                                            SplittingStrategy strategy) {
        List<CodeSegment> segments = new ArrayList<>();
        Pattern pattern = FUNCTION_PATTERNS.get(language);
        
        if (pattern == null) {
            // Fallback to line-based splitting for unsupported languages
            return splitByLines(filePath, content, language, strategy);
        }
        
        String[] lines = content.split("\\n");
        List<FunctionBoundary> functions = findFunctionBoundaries(lines, pattern);
        
        for (FunctionBoundary func : functions) {
            String funcContent = String.join("\n", 
                Arrays.copyOfRange(lines, func.startLine - 1, func.endLine));
            
            // Skip very small functions unless explicitly requested
            if (funcContent.trim().split("\n").length < strategy.minSegmentLines() && 
                strategy.minSegmentLines() > 1) {
                continue;
            }
            
            segments.add(CodeSegment.function(filePath, funcContent, 
                func.startLine, func.endLine, func.name, language));
        }
        
        return segments;
    }
    
    /**
     * Split content by classes/interfaces.
     */
    private List<CodeSegment> splitByClass(String filePath, String content, String language,
                                         SplittingStrategy strategy) {
        List<CodeSegment> segments = new ArrayList<>();
        Pattern pattern = CLASS_PATTERNS.get(language);
        
        if (pattern == null) {
            // Fallback to function-based splitting
            return splitByFunction(filePath, content, language, strategy);
        }
        
        String[] lines = content.split("\\n");
        List<ClassBoundary> classes = findClassBoundaries(lines, pattern);
        
        for (ClassBoundary cls : classes) {
            String classContent = String.join("\n",
                Arrays.copyOfRange(lines, cls.startLine - 1, cls.endLine));
            
            // Large classes can be further split by functions if requested
            if (classContent.split("\n").length > strategy.maxSegmentLines()) {
                if (strategy.allowNestedSplitting()) {
                    SplittingStrategy nestedStrategy = strategy.withType(SplittingType.FUNCTION);
                    segments.addAll(splitByFunction(filePath, classContent, language, nestedStrategy));
                    continue;
                }
            }
            
            segments.add(CodeSegment.clazz(filePath, classContent,
                cls.startLine, cls.endLine, cls.name, language));
        }
        
        return segments;
    }
    
    /**
     * Split content by fixed line windows.
     */
    private List<CodeSegment> splitByLines(String filePath, String content, String language,
                                         SplittingStrategy strategy) {
        List<CodeSegment> segments = new ArrayList<>();
        String[] lines = content.split("\\n");
        int windowSize = strategy.lineWindowSize();
        int overlap = strategy.windowOverlap();
        
        for (int i = 0; i < lines.length; i += (windowSize - overlap)) {
            int startLine = i + 1;
            int endLine = Math.min(i + windowSize, lines.length);
            
            String segmentContent = String.join("\n", 
                Arrays.copyOfRange(lines, i, endLine));
            
            // Skip empty or very small segments
            if (segmentContent.trim().isEmpty() || 
                endLine - startLine + 1 < strategy.minSegmentLines()) {
                continue;
            }
            
            segments.add(CodeSegment.of(filePath, segmentContent, startLine, endLine));
        }
        
        return segments;
    }
    
    /**
     * Intelligent splitting that combines multiple strategies.
     */
    private List<CodeSegment> splitIntelligently(String filePath, String content, String language,
                                               SplittingStrategy strategy) {
        // First try class-based splitting
        List<CodeSegment> segments = splitByClass(filePath, content, language, strategy);
        
        // If no classes found or segments are too large, try function-based
        if (segments.isEmpty() || segments.stream().anyMatch(s -> 
            s.content().split("\n").length > strategy.maxSegmentLines())) {
            segments = splitByFunction(filePath, content, language, strategy);
        }
        
        // Final fallback to line-based splitting for oversized segments
        List<CodeSegment> finalSegments = new ArrayList<>();
        for (CodeSegment segment : segments) {
            if (segment.content().split("\n").length > strategy.maxSegmentLines()) {
                finalSegments.addAll(splitByLines(segment.filePath(), segment.content(), 
                    segment.language(), strategy));
            } else {
                finalSegments.add(segment);
            }
        }
        
        return finalSegments.isEmpty() ? 
            splitByLines(filePath, content, language, strategy) : finalSegments;
    }
    
    /**
     * Create a segment for the entire file.
     */
    private CodeSegment createFileSegment(String filePath, String content, String language) {
        String[] lines = content.split("\n");
        return new CodeSegment(filePath, content, 1, lines.length, language,
            StaticAnalyzer.SegmentType.FILE, StaticAnalyzer.SegmentMetadata.empty());
    }
    
    /**
     * Extract actual content from git diff patch.
     */
    private String extractContentFromPatch(String patch) {
        if (patch == null || patch.trim().isEmpty()) {
            return "";
        }
        
        List<String> contentLines = new ArrayList<>();
        String[] lines = patch.split("\n");
        
        for (String line : lines) {
            // Skip diff headers and context markers
            if (line.startsWith("@@") || line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            
            // Include added and context lines, skip deleted lines
            if (line.startsWith("+")) {
                contentLines.add(line.substring(1)); // Remove '+' prefix
            } else if (line.startsWith(" ")) {
                contentLines.add(line.substring(1)); // Remove ' ' prefix for context
            }
            // Skip lines starting with '-' (deletions)
        }
        
        return String.join("\n", contentLines);
    }
    
    /**
     * Find function boundaries in code lines.
     */
    private List<FunctionBoundary> findFunctionBoundaries(String[] lines, Pattern pattern) {
        List<FunctionBoundary> functions = new ArrayList<>();
        Stack<Integer> braceStack = new Stack<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String functionName = extractFunctionName(matcher);
                
                // Find the end of this function by tracking braces
                int startLine = i + 1;
                int endLine = findFunctionEnd(lines, i);
                
                if (endLine > startLine) {
                    functions.add(new FunctionBoundary(functionName, startLine, endLine));
                }
            }
        }
        
        return functions;
    }
    
    /**
     * Find class boundaries in code lines.
     */
    private List<ClassBoundary> findClassBoundaries(String[] lines, Pattern pattern) {
        List<ClassBoundary> classes = new ArrayList<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String className = extractClassName(matcher);
                
                int startLine = i + 1;
                int endLine = findClassEnd(lines, i);
                
                if (endLine > startLine) {
                    classes.add(new ClassBoundary(className, startLine, endLine));
                }
            }
        }
        
        return classes;
    }
    
    /**
     * Find the end line of a function by tracking braces.
     */
    private int findFunctionEnd(String[] lines, int startIndex) {
        int braceCount = 0;
        boolean foundOpenBrace = false;
        
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];
            
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    foundOpenBrace = true;
                } else if (c == '}') {
                    braceCount--;
                    if (foundOpenBrace && braceCount == 0) {
                        return i + 1; // +1 because lines are 0-indexed
                    }
                }
            }
        }
        
        // If no closing brace found, assume end of available content
        return lines.length;
    }
    
    /**
     * Find the end line of a class by tracking braces.
     */
    private int findClassEnd(String[] lines, int startIndex) {
        return findFunctionEnd(lines, startIndex); // Same logic for now
    }
    
    /**
     * Extract function name from regex matcher.
     */
    private String extractFunctionName(Matcher matcher) {
        // Try to get the first non-null group
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (group != null && !group.trim().isEmpty()) {
                return group.trim();
            }
        }
        return "unknown";
    }
    
    /**
     * Extract class name from regex matcher.
     */
    private String extractClassName(Matcher matcher) {
        // Look for the class name group (usually the last group)
        for (int i = matcher.groupCount(); i >= 1; i--) {
            String group = matcher.group(i);
            if (group != null && !group.trim().isEmpty() && 
                !group.equals("class") && !group.equals("interface") && !group.equals("enum")) {
                return group.trim();
            }
        }
        return "unknown";
    }
    
    /**
     * Detect programming language from file extension.
     */
    private String detectLanguage(String filePath) {
        String extension = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filePath.substring(lastDot + 1).toLowerCase();
        }
        
        return switch (extension) {
            case "java" -> "java";
            case "js", "jsx", "mjs" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "py", "pyw" -> "python";
            case "cpp", "cc", "cxx", "hpp", "h" -> "cpp";
            case "c" -> "c";
            case "cs" -> "csharp";
            case "go" -> "go";
            case "rs" -> "rust";
            case "kt", "kts" -> "kotlin";
            case "scala", "sc" -> "scala";
            case "rb" -> "ruby";
            case "php" -> "php";
            case "swift" -> "swift";
            case "dart" -> "dart";
            default -> "text";
        };
    }
    
    /**
     * Splitting strategy configuration.
     */
    public static class SplittingStrategy {
        private final SplittingType type;
        private final int lineWindowSize;
        private final int windowOverlap;
        private final int minSegmentLines;
        private final int maxSegmentLines;
        private final boolean allowNestedSplitting;
        private final Set<String> excludePatterns;
        
        private SplittingStrategy(SplittingType type, int lineWindowSize, int windowOverlap,
                                int minSegmentLines, int maxSegmentLines, boolean allowNestedSplitting,
                                Set<String> excludePatterns) {
            this.type = type;
            this.lineWindowSize = lineWindowSize;
            this.windowOverlap = windowOverlap;
            this.minSegmentLines = minSegmentLines;
            this.maxSegmentLines = maxSegmentLines;
            this.allowNestedSplitting = allowNestedSplitting;
            this.excludePatterns = excludePatterns;
        }
        
        public static SplittingStrategy byFunction() {
            return new SplittingStrategy(SplittingType.FUNCTION, 50, 5, 3, 200, false, Set.of());
        }
        
        public static SplittingStrategy byClass() {
            return new SplittingStrategy(SplittingType.CLASS, 50, 5, 10, 500, true, Set.of());
        }
        
        public static SplittingStrategy byLines(int windowSize) {
            return new SplittingStrategy(SplittingType.LINES, windowSize, 0, 1, windowSize, false, Set.of());
        }
        
        public static SplittingStrategy byLines(int windowSize, int overlap) {
            return new SplittingStrategy(SplittingType.LINES, windowSize, overlap, 1, windowSize, false, Set.of());
        }
        
        public static SplittingStrategy intelligent() {
            return new SplittingStrategy(SplittingType.INTELLIGENT, 100, 10, 5, 300, true, Set.of());
        }
        
        public static SplittingStrategy wholeFile() {
            return new SplittingStrategy(SplittingType.FILE, 0, 0, 1, Integer.MAX_VALUE, false, Set.of());
        }
        
        // Builder methods
        public SplittingStrategy withMinLines(int minLines) {
            return new SplittingStrategy(type, lineWindowSize, windowOverlap, minLines, maxSegmentLines,
                allowNestedSplitting, excludePatterns);
        }
        
        public SplittingStrategy withMaxLines(int maxLines) {
            return new SplittingStrategy(type, lineWindowSize, windowOverlap, minSegmentLines, maxLines,
                allowNestedSplitting, excludePatterns);
        }
        
        public SplittingStrategy withNestedSplitting(boolean nested) {
            return new SplittingStrategy(type, lineWindowSize, windowOverlap, minSegmentLines, maxSegmentLines,
                nested, excludePatterns);
        }
        
        public SplittingStrategy withType(SplittingType newType) {
            return new SplittingStrategy(newType, lineWindowSize, windowOverlap, minSegmentLines, maxSegmentLines,
                allowNestedSplitting, excludePatterns);
        }
        
        // Getters
        public SplittingType type() { return type; }
        public int lineWindowSize() { return lineWindowSize; }
        public int windowOverlap() { return windowOverlap; }
        public int minSegmentLines() { return minSegmentLines; }
        public int maxSegmentLines() { return maxSegmentLines; }
        public boolean allowNestedSplitting() { return allowNestedSplitting; }
        public Set<String> excludePatterns() { return excludePatterns; }
    }
    
    /**
     * Types of code splitting strategies.
     */
    public enum SplittingType {
        /** Split by functions/methods */
        FUNCTION,
        /** Split by classes/interfaces */
        CLASS,
        /** Split by fixed line windows */
        LINES,
        /** Intelligent adaptive splitting */
        INTELLIGENT,
        /** Treat entire file as one segment */
        FILE
    }
    
    /**
     * Function boundary information.
     */
    private record FunctionBoundary(String name, int startLine, int endLine) {}
    
    /**
     * Class boundary information.
     */
    private record ClassBoundary(String name, int startLine, int endLine) {}
}
