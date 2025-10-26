package com.framstag.llmaj.tools.filestatistics;

public class FileStatistics {

    String wildcard;
    int numberOfFiles;
    int lines;

    public FileStatistics(String wildcard) {
        this.wildcard = wildcard;
        this.numberOfFiles = 0;
        this.lines = 0;
    }

    public void addAFile(int lines) {
        numberOfFiles++;
        this.lines+=lines;
    }

    @Override
    public String toString() {
        return "FileStatistics{" +
                "wildcard='" + wildcard + '\'' +
                ", numberOfFiles=" + numberOfFiles +
                ", lines=" + lines +
                '}';
    }
}
