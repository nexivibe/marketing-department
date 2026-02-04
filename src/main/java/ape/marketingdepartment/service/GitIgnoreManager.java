package ape.marketingdepartment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages .gitignore file entries for the project.
 * Allows adding/removing specific entries while preserving other content.
 */
public class GitIgnoreManager {

    public static final String AI_LOG = "ai.log";
    public static final String API_LOG = "api.log";
    public static final String HOLISTIC_LOG = "holistic.log";

    private static final String MARKER_START = "# Marketing Department logs";
    private static final String MARKER_END = "# End Marketing Department logs";

    private final Path projectDir;
    private final Path gitignorePath;

    public GitIgnoreManager(Path projectDir) {
        this.projectDir = projectDir;
        this.gitignorePath = projectDir.resolve(".gitignore");
    }

    /**
     * Check if .gitignore exists.
     */
    public boolean exists() {
        return Files.exists(gitignorePath);
    }

    /**
     * Check if a specific entry is in the .gitignore managed section.
     */
    public boolean hasEntry(String entry) {
        try {
            if (!exists()) {
                return false;
            }
            List<String> managedEntries = getManagedEntries();
            return managedEntries.contains(entry);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the entries managed by this application.
     */
    public List<String> getManagedEntries() throws IOException {
        List<String> entries = new ArrayList<>();
        if (!exists()) {
            return entries;
        }

        List<String> lines = Files.readAllLines(gitignorePath);
        boolean inManagedSection = false;

        for (String line : lines) {
            if (line.trim().equals(MARKER_START)) {
                inManagedSection = true;
                continue;
            }
            if (line.trim().equals(MARKER_END)) {
                inManagedSection = false;
                continue;
            }
            if (inManagedSection && !line.trim().isEmpty()) {
                entries.add(line.trim());
            }
        }

        return entries;
    }

    /**
     * Add an entry to the managed section of .gitignore.
     */
    public void addEntry(String entry) throws IOException {
        Set<String> entries = new LinkedHashSet<>(getManagedEntries());
        entries.add(entry);
        writeManagedSection(entries);
    }

    /**
     * Remove an entry from the managed section of .gitignore.
     */
    public void removeEntry(String entry) throws IOException {
        Set<String> entries = new LinkedHashSet<>(getManagedEntries());
        entries.remove(entry);
        writeManagedSection(entries);
    }

    /**
     * Set all managed entries at once.
     */
    public void setManagedEntries(Set<String> entries) throws IOException {
        writeManagedSection(entries);
    }

    /**
     * Initialize .gitignore with default log entries.
     */
    public void initializeWithDefaults() throws IOException {
        Set<String> defaults = new LinkedHashSet<>();
        defaults.add(AI_LOG);
        defaults.add(API_LOG);
        defaults.add(HOLISTIC_LOG);
        writeManagedSection(defaults);
    }

    /**
     * Read the entire .gitignore content.
     */
    public String getContent() throws IOException {
        if (!exists()) {
            return "";
        }
        return Files.readString(gitignorePath);
    }

    /**
     * Write the managed section while preserving other content.
     */
    private void writeManagedSection(Set<String> entries) throws IOException {
        List<String> existingLines = new ArrayList<>();
        if (exists()) {
            existingLines = new ArrayList<>(Files.readAllLines(gitignorePath));
        }

        // Find and remove existing managed section
        List<String> newLines = new ArrayList<>();
        boolean inManagedSection = false;
        boolean foundManagedSection = false;

        for (String line : existingLines) {
            if (line.trim().equals(MARKER_START)) {
                inManagedSection = true;
                foundManagedSection = true;
                continue;
            }
            if (line.trim().equals(MARKER_END)) {
                inManagedSection = false;
                continue;
            }
            if (!inManagedSection) {
                newLines.add(line);
            }
        }

        // Remove trailing blank lines before adding our section
        while (!newLines.isEmpty() && newLines.getLast().trim().isEmpty()) {
            newLines.removeLast();
        }

        // Add managed section if we have entries
        if (!entries.isEmpty()) {
            if (!newLines.isEmpty()) {
                newLines.add("");  // Blank line separator
            }
            newLines.add(MARKER_START);
            for (String entry : entries) {
                newLines.add(entry);
            }
            newLines.add(MARKER_END);
        }

        // Write the file
        Files.writeString(gitignorePath, String.join("\n", newLines) + "\n");
    }

    /**
     * Check if all default log files are ignored.
     */
    public boolean allLogsIgnored() {
        return hasEntry(AI_LOG) && hasEntry(API_LOG) && hasEntry(HOLISTIC_LOG);
    }

    /**
     * Get the path to the .gitignore file.
     */
    public Path getGitignorePath() {
        return gitignorePath;
    }
}
