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
                // For boolean sections, recursively render the content
                replacement = bool ? render(content, context) : "";
            } else {
                // Truthy value - render content once with recursive processing
                replacement = render(content, context);
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
     * Implements best-practice SEO including:
     * - Meta description
     * - Open Graph tags for social sharing
     * - Twitter Card tags
     * - Canonical URL
     * - JSON-LD structured data
     * - Semantic HTML5 structure
     */
    public static String generateDefaultTemplate() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{title}}</title>

    <!-- SEO Meta Tags -->
    <meta name="description" content="{{description}}">
    <meta name="author" content="{{author}}">
    {{#tags}}
    <meta name="keywords" content="{{tagsCommaSeparated}}">
    {{/tags}}
    <meta name="robots" content="index, follow">

    <!-- Canonical URL -->
    {{#canonicalUrl}}
    <link rel="canonical" href="{{canonicalUrl}}">
    {{/canonicalUrl}}

    <!-- Open Graph / Facebook -->
    <meta property="og:type" content="article">
    <meta property="og:title" content="{{title}}">
    <meta property="og:description" content="{{description}}">
    {{#canonicalUrl}}
    <meta property="og:url" content="{{canonicalUrl}}">
    {{/canonicalUrl}}
    <meta property="og:site_name" content="{{siteName}}">
    {{#ogImage}}
    <meta property="og:image" content="{{ogImage}}">
    {{/ogImage}}
    <meta property="article:author" content="{{author}}">
    {{#date}}
    <meta property="article:published_time" content="{{dateIso}}">
    {{/date}}

    <!-- Twitter Card -->
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="{{title}}">
    <meta name="twitter:description" content="{{description}}">
    {{#ogImage}}
    <meta name="twitter:image" content="{{ogImage}}">
    {{/ogImage}}

    {{{verificationComment}}}

    <!-- JSON-LD Structured Data -->
    <script type="application/ld+json">
    {
        "@context": "https://schema.org",
        "@type": "BlogPosting",
        "headline": "{{title}}",
        "description": "{{description}}",
        "author": {
            "@type": "Person",
            "name": "{{author}}"
        },
        {{#date}}
        "datePublished": "{{dateIso}}",
        {{/date}}
        {{#canonicalUrl}}
        "url": "{{canonicalUrl}}",
        "mainEntityOfPage": {
            "@type": "WebPage",
            "@id": "{{canonicalUrl}}"
        },
        {{/canonicalUrl}}
        "wordCount": "{{wordCount}}"
    }
    </script>

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
            {{#tagsList}}<a href="{{url}}" class="tag">{{name}}</a>{{/tagsList}}
        </div>
        {{/tags}}
    </article>
</body>
</html>
""";
    }

    /**
     * Generate default tag index template.
     * Variables: siteName, tagsList (with name, slug, posts[])
     */
    public static String generateDefaultTagIndexTemplate() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tags - {{siteName}}</title>
    <meta name="description" content="Browse all posts by tag on {{siteName}}">
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 900px;
            margin: 0 auto;
            padding: 20px;
            line-height: 1.6;
            color: #333;
        }
        h1 { color: #111; border-bottom: 2px solid #eee; padding-bottom: 10px; }
        .tag-section { margin-bottom: 40px; }
        .tag-section h2 {
            color: #444;
            font-size: 1.4em;
            margin-bottom: 15px;
            padding: 8px 15px;
            background: #f5f5f5;
            border-radius: 5px;
        }
        .tag-section h2 a { color: inherit; text-decoration: none; }
        .tag-section h2 .count { color: #888; font-weight: normal; font-size: 0.8em; }
        .post-list { list-style: none; padding: 0; margin: 0; }
        .post-list li { padding: 8px 0; border-bottom: 1px solid #eee; }
        .post-list li:last-child { border-bottom: none; }
        .post-list a { color: #2563eb; text-decoration: none; }
        .post-list a:hover { text-decoration: underline; }
        .post-date { color: #888; font-size: 0.85em; margin-left: 10px; }
        .back-link { margin-bottom: 20px; }
        .back-link a { color: #666; text-decoration: none; }
    </style>
</head>
<body>
    <div class="back-link"><a href="{{listingUrl}}">&larr; Back to all posts</a></div>
    <h1>Tags</h1>
    {{#tagsList}}
    <div class="tag-section" id="{{slug}}">
        <h2><a href="#{{slug}}">{{name}}</a> <span class="count">({{postCount}} posts)</span></h2>
        <ul class="post-list">
            {{#posts}}
            <li><a href="{{url}}">{{title}}</a><span class="post-date">{{date}}</span></li>
            {{/posts}}
        </ul>
    </div>
    {{/tagsList}}
</body>
</html>
""";
    }

    /**
     * Generate default listing template for paginated post lists.
     * Variables: siteName, pageNumber, totalPages, posts[], hasPrev, hasNext, prevUrl, nextUrl, pages[]
     */
    public static String generateDefaultListingTemplate() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{#isFirstPage}}Blog{{/isFirstPage}}{{^isFirstPage}}Blog - Page {{pageNumber}}{{/isFirstPage}} - {{siteName}}</title>
    <meta name="description" content="{{#isFirstPage}}Latest posts from {{siteName}}{{/isFirstPage}}{{^isFirstPage}}Page {{pageNumber}} of posts from {{siteName}}{{/isFirstPage}}">
    {{#canonicalUrl}}<link rel="canonical" href="{{canonicalUrl}}">{{/canonicalUrl}}
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            line-height: 1.6;
            color: #333;
        }
        h1 { color: #111; }
        .post-item {
            margin-bottom: 30px;
            padding-bottom: 30px;
            border-bottom: 1px solid #eee;
        }
        .post-item:last-child { border-bottom: none; }
        .post-item h2 { margin: 0 0 8px 0; font-size: 1.4em; }
        .post-item h2 a { color: #2563eb; text-decoration: none; }
        .post-item h2 a:hover { text-decoration: underline; }
        .post-meta { color: #666; font-size: 0.9em; margin-bottom: 10px; }
        .post-meta span { margin-right: 15px; }
        .post-excerpt { color: #444; }
        .post-tags { margin-top: 10px; }
        .post-tags a {
            display: inline-block;
            background: #e8e8e8;
            color: #555;
            padding: 2px 10px;
            border-radius: 15px;
            margin-right: 5px;
            font-size: 0.8em;
            text-decoration: none;
        }
        .pagination {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 10px;
            margin-top: 40px;
            flex-wrap: wrap;
        }
        .pagination a, .pagination span {
            padding: 8px 15px;
            border: 1px solid #ddd;
            border-radius: 5px;
            text-decoration: none;
            color: #333;
        }
        .pagination a:hover { background: #f5f5f5; }
        .pagination .current { background: #2563eb; color: white; border-color: #2563eb; }
        .pagination .disabled { color: #ccc; cursor: not-allowed; }
    </style>
</head>
<body>
    <h1>{{#isFirstPage}}Latest Posts{{/isFirstPage}}{{^isFirstPage}}Posts - Page {{pageNumber}}{{/isFirstPage}}</h1>

    {{#posts}}
    <article class="post-item">
        <h2><a href="{{url}}">{{title}}</a></h2>
        <div class="post-meta">
            <span>By {{author}}</span>
            <span>{{date}}</span>
            <span>{{readTime}}</span>
        </div>
        {{#description}}<p class="post-excerpt">{{description}}</p>{{/description}}
        {{#hasTags}}
        <div class="post-tags">
            {{#tags}}<a href="{{tagUrl}}">{{tagName}}</a>{{/tags}}
        </div>
        {{/hasTags}}
    </article>
    {{/posts}}

    {{#hasMultiplePages}}
    <nav class="pagination">
        {{#hasPrev}}<a href="{{prevUrl}}">&larr; Newer</a>{{/hasPrev}}
        {{^hasPrev}}<span class="disabled">&larr; Newer</span>{{/hasPrev}}

        {{#pages}}
        {{#isCurrent}}<span class="current">{{number}}</span>{{/isCurrent}}
        {{^isCurrent}}<a href="{{url}}">{{number}}</a>{{/isCurrent}}
        {{/pages}}

        {{#hasNext}}<a href="{{nextUrl}}">Older &rarr;</a>{{/hasNext}}
        {{^hasNext}}<span class="disabled">Older &rarr;</span>{{/hasNext}}
    </nav>
    {{/hasMultiplePages}}
</body>
</html>
""";
    }

    /**
     * Load template from file, or generate specified default if not found.
     */
    public static String loadOrCreateTemplate(Path projectPath, String templateName, String defaultContent) throws IOException {
        Path templatePath = projectPath.resolve(templateName);

        if (Files.exists(templatePath)) {
            return Files.readString(templatePath);
        }

        // Create with provided default
        Files.writeString(templatePath, defaultContent);
        return defaultContent;
    }
}
