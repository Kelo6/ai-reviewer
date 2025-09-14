/**
 * AI Reviewer - Diff Viewer Component
 * 用于解析和渲染代码差异的左右对比视图
 */

class DiffViewer {
    constructor() {
        this.parseDiff = this.parseDiff.bind(this);
        this.renderDiff = this.renderDiff.bind(this);
    }

    /**
     * 解析diff文本并生成左右对比的行数据
     */
    parseDiff(diffText) {
        const lines = diffText.split('\n');
        const leftLines = [];
        const rightLines = [];
        let leftLineNumber = 1;
        let rightLineNumber = 1;
        let isInHunk = false;

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            
            // 跳过文件头
            if (line.startsWith('diff --git') || line.startsWith('index ') || 
                line.startsWith('---') || line.startsWith('+++')) {
                continue;
            }
            
            // 解析hunk头 @@ -start,count +start,count @@
            if (line.startsWith('@@')) {
                const hunkMatch = line.match(/@@ -(\d+),?\d* \+(\d+),?\d* @@/);
                if (hunkMatch) {
                    leftLineNumber = parseInt(hunkMatch[1]);
                    rightLineNumber = parseInt(hunkMatch[2]);
                    isInHunk = true;
                    
                    // 添加hunk分隔符
                    leftLines.push({
                        type: 'hunk-header',
                        lineNumber: '',
                        content: line,
                        originalLine: line
                    });
                    rightLines.push({
                        type: 'hunk-header',
                        lineNumber: '',
                        content: line,
                        originalLine: line
                    });
                }
                continue;
            }

            if (!isInHunk) continue;

            const firstChar = line.charAt(0);
            const content = line.substring(1);

            if (firstChar === '-') {
                // 删除的行 - 只在左侧显示
                leftLines.push({
                    type: 'removed',
                    lineNumber: leftLineNumber++,
                    content: content,
                    originalLine: line
                });
                // 右侧显示空行
                rightLines.push({
                    type: 'empty',
                    lineNumber: '',
                    content: '',
                    originalLine: ''
                });
            } else if (firstChar === '+') {
                // 添加的行 - 只在右侧显示
                rightLines.push({
                    type: 'added',
                    lineNumber: rightLineNumber++,
                    content: content,
                    originalLine: line
                });
                // 左侧显示空行
                leftLines.push({
                    type: 'empty',
                    lineNumber: '',
                    content: '',
                    originalLine: ''
                });
            } else {
                // 上下文行 - 两侧都显示
                leftLines.push({
                    type: 'context',
                    lineNumber: leftLineNumber++,
                    content: content,
                    originalLine: line
                });
                rightLines.push({
                    type: 'context',
                    lineNumber: rightLineNumber++,
                    content: content,
                    originalLine: line
                });
            }
        }

        return { leftLines, rightLines };
    }

    /**
     * 渲染diff视图
     */
    renderDiff(filename, diffText, container) {
        if (!diffText || diffText.trim() === '') {
            container.innerHTML = '<p class="text-muted">暂无代码差异</p>';
            return;
        }

        const { leftLines, rightLines } = this.parseDiff(diffText);
        
        // 确保两侧行数一致
        while (leftLines.length < rightLines.length) {
            leftLines.push({
                type: 'empty',
                lineNumber: '',
                content: '',
                originalLine: ''
            });
        }
        while (rightLines.length < leftLines.length) {
            rightLines.push({
                type: 'empty',
                lineNumber: '',
                content: '',
                originalLine: ''
            });
        }

        const diffViewer = document.createElement('div');
        diffViewer.className = 'diff-viewer';

        // 头部
        const diffHeader = document.createElement('div');
        diffHeader.className = 'diff-header';
        diffHeader.innerHTML = `
            <span>📄</span>
            <span>${this.escapeHtml(filename)}</span>
        `;

        // 内容区域
        const diffContent = document.createElement('div');
        diffContent.className = 'diff-content';

        // 左侧（删除）
        const leftSide = document.createElement('div');
        leftSide.className = 'diff-side';
        
        const leftHeader = document.createElement('div');
        leftHeader.className = 'diff-side-header';
        leftHeader.textContent = '变更前';
        leftSide.appendChild(leftHeader);

        // 右侧（添加）
        const rightSide = document.createElement('div');
        rightSide.className = 'diff-side';
        
        const rightHeader = document.createElement('div');
        rightHeader.className = 'diff-side-header';
        rightHeader.textContent = '变更后';
        rightSide.appendChild(rightHeader);

        // 渲染行
        for (let i = 0; i < leftLines.length; i++) {
            const leftLine = leftLines[i];
            const rightLine = rightLines[i];

            // 左侧行
            const leftDiffLine = this.createDiffLine(leftLine);
            leftSide.appendChild(leftDiffLine);

            // 右侧行
            const rightDiffLine = this.createDiffLine(rightLine);
            rightSide.appendChild(rightDiffLine);
        }

        diffContent.appendChild(leftSide);
        diffContent.appendChild(rightSide);
        
        diffViewer.appendChild(diffHeader);
        diffViewer.appendChild(diffContent);

        container.innerHTML = '';
        container.appendChild(diffViewer);
    }

    /**
     * 创建单行diff元素
     */
    createDiffLine(lineData) {
        const diffLine = document.createElement('div');
        diffLine.className = `diff-line ${lineData.type}`;

        const lineNumber = document.createElement('div');
        lineNumber.className = 'diff-line-number';
        lineNumber.textContent = lineData.lineNumber || '';

        const lineContent = document.createElement('div');
        lineContent.className = 'diff-line-content';
        lineContent.textContent = lineData.content;

        diffLine.appendChild(lineNumber);
        diffLine.appendChild(lineContent);

        return diffLine;
    }

    /**
     * HTML转义
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * 初始化所有diff视图
     */
    initializeAll() {
        const diffContainers = document.querySelectorAll('[data-diff-patch]');
        diffContainers.forEach(container => {
            const filename = container.getAttribute('data-diff-filename') || '';
            const patch = container.getAttribute('data-diff-patch') || '';
            
            if (patch) {
                this.renderDiff(filename, patch, container);
            }
        });
    }
}

// 页面加载完成后初始化diff视图
document.addEventListener('DOMContentLoaded', function() {
    const diffViewer = new DiffViewer();
    diffViewer.initializeAll();
});

// 导出给其他脚本使用
window.DiffViewer = DiffViewer;
