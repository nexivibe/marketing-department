package ape.marketingdepartment.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for validating Mustache templates.
 *
 * IMPORTANT: Always validate template correctness after AI modifications.
 * AI may generate invalid Mustache syntax, unclosed tags, or break the template structure.
 * This service checks for common issues and validates the template can be rendered.
 */
public class TemplateValidationService {

    // Known template variables provided by WebExportService
    private static final Set<String> KNOWN_VARIABLES = Set.of(
            "title", "author", "date", "dateIso", "readTime",
            "description", "content", "tags", "tagsList", "tagsCommaSeparated",
            "canonicalUrl", "siteName", "ogImage", "wordCount",
            "urlBase", "verificationComment",
            // Tag object properties
            "name", "url",
            // Special Mustache
            "."
    );

    // Known section names
    private static final Set<String> KNOWN_SECTIONS = Set.of(
            "tags", "tagsList", "canonicalUrl", "ogImage", "date"
    );

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\{?([^}#^/]+)\\}?\\}\\}");
    private static final Pattern SECTION_OPEN_PATTERN = Pattern.compile("\\{\\{#([^}]+)\\}\\}");
    private static final Pattern SECTION_CLOSE_PATTERN = Pattern.compile("\\{\\{/([^}]+)\\}\\}");
    private static final Pattern INVERTED_PATTERN = Pattern.compile("\\{\\{\\^([^}]+)\\}\\}");

    /**
     * Validate a Mustache template.
     *
     * @param template The template content to validate
     * @return Validation result with errors, warnings, and found variables
     */
    public ValidationResult validate(String template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> variables = new LinkedHashSet<>();
        Set<String> sections = new LinkedHashSet<>();

        if (template == null || template.isBlank()) {
            errors.add("Template is empty");
            return new ValidationResult(false, errors, warnings, variables, sections);
        }

        // Check for basic HTML structure
        if (!template.contains("<!DOCTYPE") && !template.contains("<!doctype")) {
            warnings.add("Missing <!DOCTYPE html> declaration");
        }
        if (!template.contains("<html")) {
            warnings.add("Missing <html> tag");
        }
        if (!template.contains("<head>")) {
            warnings.add("Missing <head> section");
        }
        if (!template.contains("<body>")) {
            warnings.add("Missing <body> section");
        }

        // Extract and validate Mustache syntax
        validateMustacheSyntax(template, errors, warnings, variables, sections);

        // Check for unclosed HTML tags (basic check)
        validateHtmlStructure(template, warnings);

        // Check for SEO essentials
        validateSeoElements(template, warnings);

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors, warnings, variables, sections);
    }

    private void validateMustacheSyntax(String template, List<String> errors,
                                        List<String> warnings, Set<String> variables, Set<String> sections) {
        // Track section opens/closes
        List<String> openSections = new ArrayList<>();

        // Find all section opens
        Matcher sectionMatcher = SECTION_OPEN_PATTERN.matcher(template);
        List<SectionMatch> sectionOpens = new ArrayList<>();
        while (sectionMatcher.find()) {
            String sectionName = sectionMatcher.group(1).trim();
            sections.add(sectionName);
            sectionOpens.add(new SectionMatch(sectionName, sectionMatcher.start()));

            if (!KNOWN_SECTIONS.contains(sectionName) && !KNOWN_VARIABLES.contains(sectionName)) {
                warnings.add("Unknown section: {{#" + sectionName + "}} - verify this variable is provided");
            }
        }

        // Find all section closes
        Matcher closeMatcher = SECTION_CLOSE_PATTERN.matcher(template);
        List<SectionMatch> sectionCloses = new ArrayList<>();
        while (closeMatcher.find()) {
            sectionCloses.add(new SectionMatch(closeMatcher.group(1).trim(), closeMatcher.start()));
        }

        // Validate matching opens/closes
        Stack<String> sectionStack = new Stack<>();
        List<Object> allMatches = new ArrayList<>();

        // Combine and sort by position
        for (SectionMatch m : sectionOpens) {
            allMatches.add(new Object[]{m.position, "open", m.name});
        }
        for (SectionMatch m : sectionCloses) {
            allMatches.add(new Object[]{m.position, "close", m.name});
        }
        allMatches.sort((a, b) -> Integer.compare((int) ((Object[]) a)[0], (int) ((Object[]) b)[0]));

        for (Object match : allMatches) {
            Object[] arr = (Object[]) match;
            String type = (String) arr[1];
            String name = (String) arr[2];

            if ("open".equals(type)) {
                sectionStack.push(name);
            } else {
                if (sectionStack.isEmpty()) {
                    errors.add("Unexpected section close: {{/" + name + "}} with no matching open");
                } else {
                    String expected = sectionStack.pop();
                    if (!expected.equals(name)) {
                        errors.add("Mismatched section: expected {{/" + expected + "}} but found {{/" + name + "}}");
                    }
                }
            }
        }

        if (!sectionStack.isEmpty()) {
            for (String unclosed : sectionStack) {
                errors.add("Unclosed section: {{#" + unclosed + "}}");
            }
        }

        // Find all variables
        Matcher varMatcher = VAR_PATTERN.matcher(template);
        while (varMatcher.find()) {
            String varName = varMatcher.group(1).trim();
            variables.add(varName);

            if (!KNOWN_VARIABLES.contains(varName) && !sections.contains(varName)) {
                warnings.add("Unknown variable: {{" + varName + "}} - verify this variable is provided");
            }
        }

        // Check for inverted sections
        Matcher invertedMatcher = INVERTED_PATTERN.matcher(template);
        while (invertedMatcher.find()) {
            String sectionName = invertedMatcher.group(1).trim();
            sections.add("^" + sectionName);
        }

        // Check for unbalanced braces (common typo)
        int openBraces = countOccurrences(template, "{{");
        int closeBraces = countOccurrences(template, "}}");
        if (openBraces != closeBraces) {
            errors.add("Unbalanced Mustache braces: " + openBraces + " opens, " + closeBraces + " closes");
        }
    }

    private void validateHtmlStructure(String template, List<String> warnings) {
        // Basic check for common unclosed tags
        String[] importantTags = {"html", "head", "body", "article", "div", "script", "style"};

        for (String tag : importantTags) {
            int opens = countPattern(template, "<" + tag + "[^>]*>");
            int closes = countPattern(template, "</" + tag + ">");
            if (opens != closes) {
                warnings.add("Possibly unbalanced <" + tag + "> tags: " + opens + " opens, " + closes + " closes");
            }
        }
    }

    private void validateSeoElements(String template, List<String> warnings) {
        // Check for essential SEO elements
        if (!template.contains("<meta name=\"description\"")) {
            warnings.add("Missing meta description tag - important for SEO");
        }
        if (!template.contains("og:title")) {
            warnings.add("Missing Open Graph title - important for social sharing");
        }
        if (!template.contains("og:description")) {
            warnings.add("Missing Open Graph description");
        }
        if (!template.contains("twitter:card")) {
            warnings.add("Missing Twitter Card meta tags");
        }
        if (!template.contains("<link rel=\"canonical\"") && !template.contains("{{#canonicalUrl}}")) {
            warnings.add("Missing canonical URL - important for SEO");
        }
        if (!template.contains("application/ld+json")) {
            warnings.add("Missing JSON-LD structured data - helps search engines");
        }
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private int countPattern(String text, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private record SectionMatch(String name, int position) {}

    /**
     * Result of template validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final Set<String> variables;
        private final Set<String> sections;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings,
                                Set<String> variables, Set<String> sections) {
            this.valid = valid;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
            this.variables = Set.copyOf(variables);
            this.sections = Set.copyOf(sections);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public Set<String> getVariables() {
            return variables;
        }

        public Set<String> getSections() {
            return sections;
        }
    }
}
