package ape.marketingdepartment.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for JSON parsing and generation without external dependencies.
 */
public final class JsonHelper {

    private JsonHelper() {
    }

    public static String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return null;

        int colonPos = json.indexOf(':', fieldStart);
        if (colonPos == -1) return null;

        int valueStart = json.indexOf('"', colonPos);
        if (valueStart == -1) return null;

        int valueEnd = findClosingQuote(json, valueStart + 1);
        if (valueEnd == -1) return null;

        return unescapeJson(json.substring(valueStart + 1, valueEnd));
    }

    public static Long extractLongField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return null;

        int colonPos = json.indexOf(':', fieldStart);
        if (colonPos == -1) return null;

        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        int valueEnd = valueStart;
        while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
            valueEnd++;
        }

        if (valueEnd == valueStart) return null;

        try {
            return Long.parseLong(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean extractBooleanField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return null;

        int colonPos = json.indexOf(':', fieldStart);
        if (colonPos == -1) return null;

        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        if (json.regionMatches(valueStart, "true", 0, 4)) {
            return true;
        } else if (json.regionMatches(valueStart, "false", 0, 5)) {
            return false;
        }
        return null;
    }

    public static List<String> extractStringArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return result;

        int arrayStart = json.indexOf('[', fieldStart);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return result;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        int pos = 0;
        while (pos < arrayContent.length()) {
            int quoteStart = arrayContent.indexOf('"', pos);
            if (quoteStart == -1) break;

            int quoteEnd = findClosingQuote(arrayContent, quoteStart + 1);
            if (quoteEnd == -1) break;

            result.add(unescapeJson(arrayContent.substring(quoteStart + 1, quoteEnd)));
            pos = quoteEnd + 1;
        }

        return result;
    }

    public static String extractObjectField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return null;

        int colonPos = json.indexOf(':', fieldStart);
        if (colonPos == -1) return null;

        int braceStart = json.indexOf('{', colonPos);
        if (braceStart == -1) return null;

        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd == -1) return null;

        return json.substring(braceStart, braceEnd + 1);
    }

    public static List<String> extractObjectArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return result;

        int arrayStart = json.indexOf('[', fieldStart);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return result;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        int pos = 0;
        while (pos < arrayContent.length()) {
            int braceStart = arrayContent.indexOf('{', pos);
            if (braceStart == -1) break;

            int braceEnd = findMatchingBrace(arrayContent, braceStart);
            if (braceEnd == -1) break;

            result.add(arrayContent.substring(braceStart, braceEnd + 1));
            pos = braceEnd + 1;
        }

        return result;
    }

    public static int findClosingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    public static int findMatchingBrace(String json, int start) {
        if (start < 0 || start >= json.length() || json.charAt(start) != '{') {
            return -1;
        }
        int depth = 1;
        boolean inString = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    public static int findMatchingBracket(String json, int start) {
        if (start < 0 || start >= json.length() || json.charAt(start) != '[') {
            return -1;
        }
        int depth = 1;
        boolean inString = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    public static String unescapeJson(String s) {
        if (s == null) return null;
        // Use placeholder for \\ to avoid it being affected by subsequent replacements
        // e.g., "\\nexivibe" should become "\nexivibe", not newline + "exivibe"
        return s.replace("\\\\", "\u0000")  // Temporarily use null char as placeholder
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\u0000", "\\");   // Restore single backslash
    }

    public static String escapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }
}
