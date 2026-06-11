# Project2LLM

**Project2LLM** is an IntelliJ IDEA plugin designed to seamlessly pack your codebase directories, modules, or individual files into a single, structured, LLM-friendly text representation.

With the rise of Large Language Models (LLMs), feeding project context is a frequent chore. Project2LLM simplifies this workflow down to a single **Drag & Drop** action.

---

## 🚀 Features

*   **Seamless Drag & Drop Integration**: Drag any file, folder, or module directly from the IntelliJ Project View tree and drop it into any external application (such as ChatGPT, Claude, Gemini, or your favorite text editor). The plugin dynamically generates a single context file on the fly!
*   **Intelligent Internal/External Drop Detection**: Drag-and-drop actions *inside* the IDE remain completely unaffected (files are moved or refactored as normal), while drops *outside* the IDE trigger the ingestion builder.
*   **Advanced `.aiignore` Filtering**: Supports custom ignore patterns modeled after `.gitignore`. Simply drop `.aiignore` files in any directory to skip temporary build directories, dependency locks, or binary formats.
*   **Powerful Profile-Based Settings**: Configure multiple settings profiles globally or on a per-project basis. Switch between profiles effortlessly.
*   **Apache Velocity Templating**: Fully customize the output format! Tweak the generated metadata header, directory tree visualization, source code wraps, and stubs for binary or oversized files.
*   **Oversized & Binary File Protection**: Automatic thresholds replace extremely large source files and binary formats with descriptive placeholder stubs, preventing token wastage and LLM context window crashes.

---

## 🛠️ Usage

1.  **Drag and Drop**:
    *   Select any folder, module, or group of files in your IDE Project Tool Window.
    *   Drag and drop them into your browser (e.g., ChatGPT, Claude web interface), LLM desktop clients, or any external text editor.
2.  **Configuration**:
    *   Go to `Settings/Preferences` -> `Tools` -> `Project2LLM Settings` (Global application settings) or `Project2LLM Project Settings` (Project-specific profiles).
    *   Create, copy, delete, and switch profiles. Customize tree prefixes, path separators, file size limits, and template formats.
3.  **Ignoring Files**:
    *   Add a `.aiignore` file to your project root or subdirectories.
    *   Add glob-like ignore rules (e.g., `*.log`, `/build`, `node_modules/`, `**/*.png`).

---

## 📋 Example Generated Output Format

When dropped externally, the plugin compiles the selection into a unified text file containing:

1.  **Workspace Specifications Header**: Project name, SDK, detected project ecosystems (Maven, Gradle, Cargo, Go, Node.js, etc.), and overall statistics.
2.  **Visual Directory Tree**: A clean ASCII tree of your included files.
3.  **Source Code Blocks**: Grouped files wrapped in customized Markdown code blocks with appropriate file extension tags for accurate LLM syntax highlighting.