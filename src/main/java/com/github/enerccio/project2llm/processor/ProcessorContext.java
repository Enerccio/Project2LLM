package com.github.enerccio.project2llm.processor;

public class ProcessorContext {

    private String suffix = ".txt";
    private long maxFileSize = 2 * 1024 * 1024;
    private String treeBranch = "├── ";
    private String treeLeaf = "└── ";
    private String separator = "/";
    private String treeSpacing = "    ";
    private String treeNext = "│   ";

    private String topHeaderTemplate = """
========================================================================
PROJECT ARCHITECTURE DIRECTORY TREE
========================================================================
""";

    private String metaInfoTemplate = """
========================================================================
🤖 LLM INGESTION METADATA HEADER
========================================================================
INSTRUCTION: This file is a consolidated repository layout payload generated
directly from ${product}. Use the provided directory tree
to map file inheritance and architectures before executing requested operations.

--- WORKSPACE SPECIFICATIONS ---
Project Name:       ${projectName}
Target Context:     ${targetContext}
#if($sdk)
Project SDK/Runtime:${sdk}
#end
Detected Ecosystem: ${detectedEcosystem}
#if($hasModuleRoots)
Modules:
#foreach($m in $modules)
Module Name: ${m.name}
Module Root: ${m.moduleRoot}
#end
#end
Payload Summary:    ${fileCount} files packed | ${totalSizeKb} KB total size
========================================================================
""";

    private String treeTemplate = """
========================================================================
TREE STRUCTURE
========================================================================

${tree}


""";

    private String sourceFileTemplate = """
========================================
File: ${file}
Type: ${type} | Lines: ${lineCount}
========================================
```${extension}
${content}
```""";

    private String sourceTooLargeTemplate = """
========================================
File: ${file}
Type: ${type}
[NOTICE TO LLM: This text file was intentionally omitted from the content payload
because its physical size (${fileSizeMb} MB) exceeds the ${maxFileSize} MB maximum performance
threshold. The file exists in the directory tree structure, but its contents
have been replaced with this stub to preserve your attention context limits.]
========================================
""";

    private String binaryFile = """
========================================
File: ${file}
Type: ${type}
Description: ${description}
[NOTICE TO LLM: This binary file was intentionally omitted from the content payload due to binary file exclusion.
The file exists in the directory tree structure, but its contents
have been replaced with this stub to preserve your attention context limits.]
========================================
""";

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public String getTreeBranch() {
        return treeBranch;
    }

    public void setTreeBranch(String treeBranch) {
        this.treeBranch = treeBranch;
    }

    public String getTreeLeaf() {
        return treeLeaf;
    }

    public void setTreeLeaf(String treeLeaf) {
        this.treeLeaf = treeLeaf;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public String getTreeSpacing() {
        return treeSpacing;
    }

    public void setTreeSpacing(String treeSpacing) {
        this.treeSpacing = treeSpacing;
    }

    public String getTreeNext() {
        return treeNext;
    }

    public void setTreeNext(String treeNext) {
        this.treeNext = treeNext;
    }

    public String getTopHeaderTemplate() {
        return topHeaderTemplate;
    }

    public void setTopHeaderTemplate(String topHeaderTemplate) {
        this.topHeaderTemplate = topHeaderTemplate;
    }

    public String getMetaInfoTemplate() {
        return metaInfoTemplate;
    }

    public void setMetaInfoTemplate(String metaInfoTemplate) {
        this.metaInfoTemplate = metaInfoTemplate;
    }

    public String getSourceFileTemplate() {
        return sourceFileTemplate;
    }

    public void setSourceFileTemplate(String sourceFileTemplate) {
        this.sourceFileTemplate = sourceFileTemplate;
    }

    public String getSourceTooLargeTemplate() {
        return sourceTooLargeTemplate;
    }

    public void setSourceTooLargeTemplate(String sourceTooLargeTemplate) {
        this.sourceTooLargeTemplate = sourceTooLargeTemplate;
    }

    public String getBinaryFile() {
        return binaryFile;
    }

    public void setBinaryFile(String binaryFile) {
        this.binaryFile = binaryFile;
    }

    public String getTreeTemplate() {
        return treeTemplate;
    }

    public void setTreeTemplate(String treeTemplate) {
        this.treeTemplate = treeTemplate;
    }
}
