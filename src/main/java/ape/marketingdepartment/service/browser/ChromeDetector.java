package ape.marketingdepartment.service.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects Chrome browser installations across different operating systems.
 */
public class ChromeDetector {

    public enum OS {
        WINDOWS, MACOS, LINUX, UNKNOWN
    }

    /**
     * Detect the current operating system.
     */
    public static OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return OS.MACOS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        }
        return OS.UNKNOWN;
    }

    /**
     * Get a list of common Chrome installation paths for the current OS.
     */
    public static List<String> getCommonChromePaths() {
        OS os = detectOS();
        List<String> paths = new ArrayList<>();

        switch (os) {
            case WINDOWS -> {
                String localAppData = System.getenv("LOCALAPPDATA");
                String programFiles = System.getenv("ProgramFiles");
                String programFilesX86 = System.getenv("ProgramFiles(x86)");
                String userHome = System.getProperty("user.home");

                if (localAppData != null) {
                    paths.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
                }
                if (programFiles != null) {
                    paths.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
                }
                if (programFilesX86 != null) {
                    paths.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
                }
                // Chromium
                if (localAppData != null) {
                    paths.add(localAppData + "\\Chromium\\Application\\chrome.exe");
                }
                // Edge (Chromium-based, can be used as fallback)
                if (programFilesX86 != null) {
                    paths.add(programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
                }
                if (programFiles != null) {
                    paths.add(programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
                }
            }
            case MACOS -> {
                paths.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
                paths.add("/Applications/Chromium.app/Contents/MacOS/Chromium");
                paths.add(System.getProperty("user.home") + "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
                // Homebrew installations
                paths.add("/opt/homebrew/bin/chromium");
                paths.add("/usr/local/bin/chromium");
            }
            case LINUX -> {
                // Common Linux paths
                paths.add("/usr/bin/google-chrome");
                paths.add("/usr/bin/google-chrome-stable");
                paths.add("/usr/bin/chromium");
                paths.add("/usr/bin/chromium-browser");
                paths.add("/snap/bin/chromium");
                // Flatpak
                paths.add("/var/lib/flatpak/exports/bin/com.google.Chrome");
                // Local installations
                paths.add("/opt/google/chrome/chrome");
                paths.add("/opt/google/chrome/google-chrome");
            }
            default -> {
                // Try some generic paths
                paths.add("/usr/bin/google-chrome");
                paths.add("/usr/bin/chromium");
            }
        }

        return paths;
    }

    /**
     * Find all Chrome installations that exist on the system.
     */
    public static List<String> findInstalledChrome() {
        List<String> installed = new ArrayList<>();
        for (String path : getCommonChromePaths()) {
            if (Files.exists(Path.of(path)) && Files.isExecutable(Path.of(path))) {
                installed.add(path);
            }
        }
        return installed;
    }

    /**
     * Test if a Chrome path is valid and executable.
     */
    public static boolean testChromePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Path chromePath = Path.of(path);
        return Files.exists(chromePath) && Files.isExecutable(chromePath);
    }

    /**
     * Get the first available Chrome installation, or null if none found.
     */
    public static String findFirstAvailableChrome() {
        List<String> installed = findInstalledChrome();
        return installed.isEmpty() ? null : installed.get(0);
    }
}
