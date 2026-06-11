
<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Project2LLM Changelog

## [1.0.0] - 2026-06-11

### Added
- **Drag & Drop Context Bundler**: Drag folders, modules, or file lists directly from the IDE Project Tool Window and drop them into external applications (LLMs, web browsers, external editors) to instantly ingest them as a unified text representation.
- **Intelligent Internal Drop Protection**: Custom drop handler ensures normal IDE behavior is preserved when dropping files within IntelliJ.
- **`.aiignore` support**: Support glob-based file exclusion configurations matching the style of `.gitignore` with recursive resolution.
- **Flexible Profiles**: App-level and Project-level setting configurables allowing different custom output templates.
- **Velocity Templates Engine**: Fully customizable output headers, directory tree blocks, code enclosures, large file stubs, and binary file representations using Apache Velocity templates.
- **Smart Stubs**: Automatic stubs for binary files and configurable maximum file-size limits to safeguard LLM context windows.