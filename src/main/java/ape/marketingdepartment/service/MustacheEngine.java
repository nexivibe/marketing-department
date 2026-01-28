package ape.marketingdepartment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple Mustache template engine without external dependencies.
 *
 * Supports:
 * - {{variable}} - HTML-escaped variable substitution
 * - {{{variable}}} - Unescaped variable substitution (for HTML content)
 * - {{#section}}...{{/section}} - Section/list iteration
 * - {{^section}}...{{/section}} - Inverted section (if empty/false)
 */
public class MustacheEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\{([^}]+)\\}\\}\\}|\\{\\{([^}#^/]+)\\}\\}");
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\{\\{#([^}]+)\\}\\}(.+?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);
    private static final Pattern INVERTED_PATTERN = Pattern.compile("\\{\\{\\^([^}]+)\\}\\}(.+?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);

    /**
     * Render a Mustache template with the given context.
     *
     * @param template The template string
     * @param context  Map of variable names to values
     * @return The rendered template
     */
    public String render(String template, Map<String, Object> context) {
        if (template == null) {
            return "";
        }

        String result = template;

        // Process sections first (they may contain nested variables)
        result = processSections(result, context);

        // Process inverted sections
        result = processInvertedSections(result, context);

        // Process variables
        result = processVariables(result, context);

        return result;
    }

    private String processSections(String template, Map<String, Object> context) {
        Matcher matcher = SECTION_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String content = matcher.group(2);
            Object value = context.get(key);

            String replacement;
            if (value == null) {
                replacement = "";
            } else if (value instanceof List<?> list) {
                StringBuilder listResult = new StringBuilder();
                for (Object item : list) {
                    if (item instanceof String) {
                        // For simple string lists, replace {{.}} with the item
                        String itemContent = content.replace("{{.}}", escapeHtml((String) item));
                        listResult.append(itemContent);
                    } else if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemContext = (Map<String, Object>) item;
                        listResult.append(render(content, itemContext));
                    } else {
                        listResult.append(content.replace("{{.}}", escapeHtml(String.valueOf(item))));
                    }
                }
                replacement = listResult.toString();
            } else if (value instanceof Boolean bool) {
                replacement = bool ? content : "";
            } else {
                // Truthy value - render content once
                replacement = content;
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String processInvertedSections(String template, Map<String, Object> context) {
        Matcher matcher = INVERTED_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String content = matcher.group(2);
            Object value = context.get(key);

            String replacement;
            if (value == null ||
                (value instanceof List<?> list && list.isEmpty()) ||
                (value instanceof Boolean bool && !bool) ||
                (value instanceof String str && str.isEmpty())) {
                replacement = content;
            } else {
                replacement = "";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String processVariables(String template, Map<String, Object> context) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String unescapedKey = matcher.group(1); // {{{var}}}
            String escapedKey = matcher.group(2);   // {{var}}

            String key = unescapedKey != null ? unescapedKey.trim() : escapedKey.trim();
            Object value = context.get(key);

            String replacement;
            if (value == null) {
                replacement = "";
            } else if (unescapedKey != null) {
                // Unescaped - use as-is
                replacement = String.valueOf(value);
            } else {
                // Escaped - HTML escape
                replacement = escapeHtml(String.valueOf(value));
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Escape HTML special characters.
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Load template from file, or generate default if not found.
     *
     * @param projectPath  The project directory
     * @param templateName The template filename
     * @return The template content
     */
    public static String loadOrCreateTemplate(Path projectPath, String templateName) throws IOException {
        Path templatePath = projectPath.resolve(templateName);

        if (Files.exists(templatePath)) {
            return Files.readString(templatePath);
        }

        // Generate default template
        String defaultTemplate = generateDefaultTemplate();
        Files.writeString(templatePath, defaultTemplate);
        return defaultTemplate;
    }

    /**
     * Generate a default HTML template for blog posts.
     */
    public static String generateDefaultTemplate() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{title}}</title>
    <meta name="description" content="{{title}} by {{author}}">
    {{#tags}}
    <meta name="keywords" content="{{tagsCommaSeparated}}">
    {{/tags}}
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            line-height: 1.7;
            color: #333;
            background: #fff;
        }
        article { margin-bottom: 40px; }
        h1 {
            font-size: 2.2em;
            margin-bottom: 0.3em;
            color: #111;
        }
        .meta {
            color: #666;
            font-size: 0.9em;
            margin-bottom: 30px;
            padding-bottom: 20px;
            border-bottom: 1px solid #eee;
        }
        .meta span { margin-right: 15px; }
        .content {
            font-size: 1.1em;
        }
        .content h2 { margin-top: 1.8em; color: #222; }
        .content h3 { margin-top: 1.5em; color: #333; }
        .content p { margin: 1em 0; }
        .content ul, .content ol { margin: 1em 0; padding-left: 2em; }
        .content li { margin: 0.5em 0; }
        .content blockquote {
            margin: 1.5em 0;
            padding: 1em 1.5em;
            border-left: 4px solid #ddd;
            background: #f9f9f9;
            font-style: italic;
        }
        .content code {
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Consolas', 'Monaco', monospace;
            font-size: 0.9em;
        }
        .content pre {
            background: #f4f4f4;
            padding: 15px;
            border-radius: 5px;
            overflow-x: auto;
        }
        .content pre code {
            background: none;
            padding: 0;
        }
        .content img {
            max-width: 100%;
            height: auto;
        }
        .tags {
            margin-top: 30px;
            padding-top: 20px;
            border-top: 1px solid #eee;
        }
        .tag {
            display: inline-block;
            background: #e8e8e8;
            color: #555;
            padding: 4px 12px;
            border-radius: 20px;
            margin-right: 8px;
            margin-bottom: 8px;
            font-size: 0.85em;
            text-decoration: none;
        }
        .tag:hover { background: #ddd; }
        @media (max-width: 600px) {
            body { padding: 15px; }
            h1 { font-size: 1.8em; }
        }
    </style>
</head>
<body>
    <article>
        <h1>{{title}}</h1>
        <div class="meta">
            <span>By <strong>{{author}}</strong></span>
            <span>{{date}}</span>
            <span>{{readTime}}</span>
        </div>
        <div class="content">
            {{{content}}}
        </div>
        {{#tags}}
        <div class="tags">
            {{#tagsList}}<span class="tag">{{.}}</span>{{/tagsList}}
        </div>
        {{/tags}}
    </article>
</body>
</html>
""";
    }
}
