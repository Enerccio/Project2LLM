package com.github.enerccio.project2llm.processor;

import java.io.File;

public record PayloadDescriptor(File tempFile, int fileCount, long totalSizeBytes, int tokenCount) {

}