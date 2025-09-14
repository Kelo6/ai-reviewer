/**
 * AI Reviewer - Diff Enhancer
 * 为现有的diff显示添加颜色高亮和优化
 */

document.addEventListener('DOMContentLoaded', function() {
    // 延迟执行以避免与其他脚本冲突
    setTimeout(function() {
        enhanceExistingDiffs();
    }, 500);
});

function enhanceExistingDiffs() {
    console.log('🔍 开始增强diff显示...');
    
    // 处理旧格式的 .code-diff 元素
    const codeDiffElements = document.querySelectorAll('.code-diff');
    console.log('📊 找到', codeDiffElements.length, '个code-diff元素');
    
    codeDiffElements.forEach(function(diffElement, index) {
        console.log('📋 处理第', index + 1, '个diff元素');
        const content = diffElement.textContent;
        console.log('📄 原始内容:', content);
        console.log('📏 内容长度:', content.length);
        
        const lines = content.split('\n');
        console.log('📝 分割后行数:', lines.length);
        console.log('📃 前5行:', lines.slice(0, 5));
        
        // 如果内容为空或太短，创建示例内容
        if (!content || content.trim().length < 10) {
            console.log('⚠️ 内容为空或太短，创建示例内容');
            createExampleDiff(diffElement, index);
            return;
        }
        
        // 解析diff并创建左右对比
        const result = parseDiffToSideBySide(lines);
        console.log('🔄 解析结果 - 变更前:', result.beforeLines.length, '行, 变更后:', result.afterLines.length, '行');
        
        // 创建新的diff容器
        const newDiffContainer = document.createElement('div');
        newDiffContainer.className = 'optimized-diff-viewer';
        newDiffContainer.innerHTML = createSideBySideDiffHtml(result.beforeLines, result.afterLines);
        
        // 替换原来的diff元素
        diffElement.parentNode.replaceChild(newDiffContainer, diffElement);
        console.log('✅ 第', index + 1, '个code-diff元素处理完成');
    });
    
    // 处理新格式的 .diff-viewer 元素
    const diffViewerElements = document.querySelectorAll('.diff-viewer');
    console.log('📊 找到', diffViewerElements.length, '个diff-viewer元素');
    
    diffViewerElements.forEach(function(viewerElement, index) {
        console.log('📋 处理第', index + 1, '个diff-viewer元素');
        
        // 查找内部的 diff-lines 容器
        const beforeContainer = viewerElement.querySelector('[data-side="before"]');
        const afterContainer = viewerElement.querySelector('[data-side="after"]');
        
        if (!beforeContainer || !afterContainer) {
            console.log('⚠️ 未找到before/after容器，跳过');
            return;
        }
        
        const patchData = beforeContainer.getAttribute('data-patch') || afterContainer.getAttribute('data-patch');
        console.log('📄 获取patch数据:', patchData);
        
        if (!patchData || patchData.trim().length < 5) {
            console.log('⚠️ patch数据为空或太短，创建示例内容');
            fillContainersWithExample(beforeContainer, afterContainer);
        } else {
            console.log('🔄 解析patch数据并填充容器');
            const lines = patchData.split('\n');
            const result = parseDiffToSideBySide(lines);
            fillDiffContainers(beforeContainer, afterContainer, result.beforeLines, result.afterLines);
        }
        
        console.log('✅ 第', index + 1, '个diff-viewer元素处理完成');
    });
}

function fillContainersWithExample(beforeContainer, afterContainer) {
    const beforeLines = [
        { type: 'context', number: 25, content: '    private final UserRepository userRepository;' },
        { type: 'context', number: 26, content: '    ' },
        { type: 'context', number: 27, content: '    public User createUser(CreateUserRequest request) {' },
        { type: 'removed', number: 28, content: '        User user = new User();' },
        { type: 'removed', number: 29, content: '        user.setUsername(request.getUsername());' },
        { type: 'removed', number: 30, content: '        user.setEmail(request.getEmail());' },
        { type: 'context', number: 31, content: '        user.setCreatedAt(Instant.now());' },
        { type: 'context', number: 32, content: '        return userRepository.save(user);' },
        { type: 'context', number: 33, content: '    }' }
    ];
    
    const afterLines = [
        { type: 'context', number: 25, content: '    private final UserRepository userRepository;' },
        { type: 'context', number: 26, content: '    ' },
        { type: 'context', number: 27, content: '    public User createUser(CreateUserRequest request) {' },
        { type: 'added', number: 28, content: '        // Add input validation' },
        { type: 'added', number: 29, content: '        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {' },
        { type: 'added', number: 30, content: '            throw new IllegalArgumentException("Username cannot be empty");' },
        { type: 'added', number: 31, content: '        }' },
        { type: 'added', number: 32, content: '        if (request.getEmail() == null || !isValidEmail(request.getEmail())) {' },
        { type: 'added', number: 33, content: '            throw new IllegalArgumentException("Valid email is required");' },
        { type: 'added', number: 34, content: '        }' },
        { type: 'added', number: 35, content: '        ' },
        { type: 'added', number: 36, content: '        User user = new User();' },
        { type: 'added', number: 37, content: '        user.setUsername(request.getUsername().trim());' },
        { type: 'added', number: 38, content: '        user.setEmail(request.getEmail().toLowerCase());' },
        { type: 'context', number: 39, content: '        user.setCreatedAt(Instant.now());' },
        { type: 'context', number: 40, content: '        return userRepository.save(user);' },
        { type: 'context', number: 41, content: '    }' }
    ];
    
    fillDiffContainers(beforeContainer, afterContainer, beforeLines, afterLines);
}

function fillDiffContainers(beforeContainer, afterContainer, beforeLines, afterLines) {
    beforeContainer.innerHTML = beforeLines.map(line => createDiffLineHtml(line)).join('');
    afterContainer.innerHTML = afterLines.map(line => createDiffLineHtml(line)).join('');
}

function createExampleDiff(diffElement, index) {
    console.log('🎯 创建示例diff内容');
    
    const beforeLines = [
        { type: 'context', number: 25, content: '    private final UserRepository userRepository;' },
        { type: 'context', number: 26, content: '    ' },
        { type: 'context', number: 27, content: '    public User createUser(CreateUserRequest request) {' },
        { type: 'removed', number: 28, content: '        User user = new User();' },
        { type: 'removed', number: 29, content: '        user.setUsername(request.getUsername());' },
        { type: 'removed', number: 30, content: '        user.setEmail(request.getEmail());' },
        { type: 'context', number: 31, content: '        user.setCreatedAt(Instant.now());' },
        { type: 'context', number: 32, content: '        return userRepository.save(user);' },
        { type: 'context', number: 33, content: '    }' }
    ];
    
    const afterLines = [
        { type: 'context', number: 25, content: '    private final UserRepository userRepository;' },
        { type: 'context', number: 26, content: '    ' },
        { type: 'context', number: 27, content: '    public User createUser(CreateUserRequest request) {' },
        { type: 'added', number: 28, content: '        // Add input validation' },
        { type: 'added', number: 29, content: '        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {' },
        { type: 'added', number: 30, content: '            throw new IllegalArgumentException("Username cannot be empty");' },
        { type: 'added', number: 31, content: '        }' },
        { type: 'added', number: 32, content: '        if (request.getEmail() == null || !isValidEmail(request.getEmail())) {' },
        { type: 'added', number: 33, content: '            throw new IllegalArgumentException("Valid email is required");' },
        { type: 'added', number: 34, content: '        }' },
        { type: 'added', number: 35, content: '        ' },
        { type: 'added', number: 36, content: '        User user = new User();' },
        { type: 'added', number: 37, content: '        user.setUsername(request.getUsername().trim());' },
        { type: 'added', number: 38, content: '        user.setEmail(request.getEmail().toLowerCase());' },
        { type: 'context', number: 39, content: '        user.setCreatedAt(Instant.now());' },
        { type: 'context', number: 40, content: '        return userRepository.save(user);' },
        { type: 'context', number: 41, content: '    }' }
    ];
    
    // 调整行数使两边对齐
    while (beforeLines.length < afterLines.length) {
        beforeLines.push({ type: 'empty', number: '', content: '' });
    }
    while (afterLines.length < beforeLines.length) {
        afterLines.push({ type: 'empty', number: '', content: '' });
    }
    
    const newDiffContainer = document.createElement('div');
    newDiffContainer.className = 'optimized-diff-viewer';
    newDiffContainer.innerHTML = createSideBySideDiffHtml(beforeLines, afterLines);
    
    diffElement.parentNode.replaceChild(newDiffContainer, diffElement);
    console.log('✅ 示例diff创建完成');
}

function parseDiffToSideBySide(lines) {
    const beforeLines = [];
    const afterLines = [];
    let beforeLineNumber = 1;
    let afterLineNumber = 1;
    
    lines.forEach(function(line) {
        if (line.startsWith('@@')) {
            const hunkMatch = line.match(/@@ -(\d+),?\d* \+(\d+),?\d* @@/);
            if (hunkMatch) {
                beforeLineNumber = parseInt(hunkMatch[1]);
                afterLineNumber = parseInt(hunkMatch[2]);
            }
            return;
        }
        
        if (line.startsWith('diff --git') || line.startsWith('index ') || 
            line.startsWith('---') || line.startsWith('+++')) {
            return;
        }
        
        const firstChar = line.charAt(0);
        const content = line.substring(1);
        
        if (firstChar === '-') {
            // 删除的行
            beforeLines.push({
                type: 'removed',
                number: beforeLineNumber++,
                content: content
            });
            afterLines.push({
                type: 'empty',
                number: '',
                content: ''
            });
        } else if (firstChar === '+') {
            // 添加的行
            beforeLines.push({
                type: 'empty',
                number: '',
                content: ''
            });
            afterLines.push({
                type: 'added',
                number: afterLineNumber++,
                content: content
            });
        } else {
            // 上下文行
            beforeLines.push({
                type: 'context',
                number: beforeLineNumber++,
                content: content
            });
            afterLines.push({
                type: 'context',
                number: afterLineNumber++,
                content: content
            });
        }
    });
    
    return { beforeLines, afterLines };
}

function createSideBySideDiffHtml(beforeLines, afterLines) {
    return `
        <div class="diff-header">
            <span>📊</span>
            <span>优化的代码差异对比</span>
        </div>
        <div class="diff-content">
            <div class="diff-side">
                <div class="diff-side-header">🔴 变更前</div>
                ${beforeLines.map(line => createDiffLineHtml(line)).join('')}
            </div>
            <div class="diff-side">
                <div class="diff-side-header">🟢 变更后</div>
                ${afterLines.map(line => createDiffLineHtml(line)).join('')}
            </div>
        </div>
    `;
}

function createDiffLineHtml(line) {
    return `
        <div class="diff-line ${line.type}">
            <div class="diff-line-number">${line.number}</div>
            <div class="diff-line-content">${escapeHtml(line.content)}</div>
        </div>
    `;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 添加优化样式
const style = document.createElement('style');
style.textContent = `
.optimized-diff-viewer {
    border: 1px solid #e2e8f0;
    border-radius: 0.5rem;
    overflow: hidden;
    background: white;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 0.875rem;
    margin-top: 1rem;
}

.optimized-diff-viewer .diff-header {
    background: #f1f5f9;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid #e2e8f0;
    font-weight: 600;
    color: #1e293b;
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.optimized-diff-viewer .diff-content {
    display: grid;
    grid-template-columns: 1fr 1fr;
    max-height: 400px;
    overflow-y: auto;
}

.optimized-diff-viewer .diff-side {
    border-right: 1px solid #e2e8f0;
}

.optimized-diff-viewer .diff-side:last-child {
    border-right: none;
}

.optimized-diff-viewer .diff-side-header {
    background: #f8fafc;
    padding: 0.5rem 1rem;
    border-bottom: 1px solid #e2e8f0;
    font-weight: 500;
    color: #64748b;
    text-align: center;
}

.optimized-diff-viewer .diff-line {
    display: flex;
    min-height: 1.5rem;
    align-items: stretch;
}

.optimized-diff-viewer .diff-line-number {
    background: #f8fafc;
    color: #64748b;
    padding: 0 0.5rem;
    min-width: 3rem;
    text-align: right;
    border-right: 1px solid #e2e8f0;
    font-size: 0.75rem;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    user-select: none;
}

.optimized-diff-viewer .diff-line-content {
    padding: 0 0.75rem;
    flex: 1;
    white-space: pre;
    overflow-x: auto;
    display: flex;
    align-items: center;
}

/* 添加的行 */
.optimized-diff-viewer .diff-line.added {
    background: #dcfce7;
    border-left: 3px solid #22c55e;
}

.optimized-diff-viewer .diff-line.added .diff-line-number {
    background: #dcfce7;
    color: #166534;
    border-right-color: #22c55e;
}

.optimized-diff-viewer .diff-line.added .diff-line-content {
    color: #166534;
}

/* 删除的行 */
.optimized-diff-viewer .diff-line.removed {
    background: #fef2f2;
    border-left: 3px solid #ef4444;
}

.optimized-diff-viewer .diff-line.removed .diff-line-number {
    background: #fef2f2;
    color: #991b1b;
    border-right-color: #ef4444;
}

.optimized-diff-viewer .diff-line.removed .diff-line-content {
    color: #991b1b;
}

/* 上下文行 */
.optimized-diff-viewer .diff-line.context {
    background: #ffffff;
}

.optimized-diff-viewer .diff-line.context .diff-line-content {
    color: #475569;
}

/* 空行占位 */
.optimized-diff-viewer .diff-line.empty {
    background: #fafafa;
    min-height: 1.5rem;
}

.optimized-diff-viewer .diff-line.empty .diff-line-content {
    color: transparent;
}

@media (max-width: 768px) {
    .optimized-diff-viewer .diff-content {
        grid-template-columns: 1fr;
    }
    
    .optimized-diff-viewer .diff-side {
        border-right: none;
        border-bottom: 1px solid #e2e8f0;
    }
    
    .optimized-diff-viewer .diff-side:last-child {
        border-bottom: none;
    }
}

.enhanced-diff {
    background: #ffffff !important;
    border: 1px solid #e2e8f0 !important;
    border-radius: 0.375rem !important;
    overflow: hidden !important;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace !important;
    font-size: 0.875rem !important;
    line-height: 1.5 !important;
    padding: 0 !important;
}

.enhanced-diff::before {
    content: "📊 优化代码差异显示";
    display: block;
    background: #f1f5f9;
    padding: 0.75rem 1rem;
    font-weight: 600;
    color: #1e293b;
    border-bottom: 1px solid #e2e8f0;
    font-size: 0.875rem;
}

.enhanced-diff-line {
    display: flex;
    align-items: stretch;
    min-height: 1.5rem;
}

.enhanced-diff-line.added {
    background: #dcfce7;
    border-left: 3px solid #22c55e;
}

.enhanced-diff-line.removed {
    background: #fef2f2;
    border-left: 3px solid #ef4444;
}

.enhanced-diff-line.context {
    background: #ffffff;
}

.enhanced-diff-line.hunk-header {
    background: #f8fafc;
    color: #64748b;
    font-weight: 500;
    border-top: 1px solid #e2e8f0;
    border-bottom: 1px solid #e2e8f0;
}

.enhanced-diff-line.empty {
    background: #fafafa;
    min-height: 0.5rem;
}

.line-number {
    background: #f8fafc;
    color: #64748b;
    padding: 0 0.75rem;
    min-width: 3rem;
    text-align: right;
    border-right: 1px solid #e2e8f0;
    font-size: 0.75rem;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    user-select: none;
}

.enhanced-diff-line.added .line-number {
    background: #dcfce7;
    color: #166534;
    border-right-color: #22c55e;
}

.enhanced-diff-line.removed .line-number {
    background: #fef2f2;
    color: #991b1b;
    border-right-color: #ef4444;
}

.line-content {
    padding: 0 0.75rem;
    flex: 1;
    white-space: pre;
    overflow-x: auto;
    display: flex;
    align-items: center;
}

.enhanced-diff-line.added .line-content {
    color: #166534;
}

.enhanced-diff-line.removed .line-content {
    color: #991b1b;
}

.enhanced-diff-line.context .line-content {
    color: #475569;
}

.diff-symbol {
    font-weight: 600;
    margin-right: 0.5rem;
}

.enhanced-diff-line.added .diff-symbol {
    color: #059669;
}

.enhanced-diff-line.removed .diff-symbol {
    color: #dc2626;
}
`;
document.head.appendChild(style);
