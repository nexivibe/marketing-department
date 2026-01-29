# Marketing Department - Claude Code Guide

## What This Is

A JavaFX desktop application for broadcasting authentic human content across 13+ social media platforms using AI-powered transformation. One markdown post becomes optimized content for LinkedIn, Twitter, Instagram, Dev.to, and more.

## Core Philosophy

Take a single piece of authentic human writing and let AI handle the tedious work of reformatting it for every platform's unique requirements—character limits, hashtag conventions, professional vs. casual tone, etc.

## Tech Stack

- **Java 21** + **JavaFX 17** (cross-platform desktop app)
- **Maven** build system with shade plugin for fat JAR
- **Grok AI** (x.ai API) for content transformation
- **GetLate API** for unified social media publishing
- **Dev.to API** for technical article publishing
- **Flexmark** for Markdown parsing
- **Mustache** for HTML templating

## Architecture

```
src/main/java/ape/marketingdepartment/
├── MarketingApp.java          # JavaFX entry point
├── controller/                 # UI controllers
│   ├── ProjectController       # Main workspace
│   ├── PipelineExecutionController  # Publishing orchestration
│   └── MetaEditorPopupController    # Post metadata editing
├── model/                      # Domain objects (JSON-persisted)
│   ├── Project, Post, PostStatus
│   ├── Pipeline, PipelineStage, PipelineExecution
│   ├── PublishingProfile, ProjectSettings
│   └── PlatformTransform, WebTransform
└── service/                    # Business logic
    ├── WebExportService        # HTML export with templates
    ├── ai/GrokService          # x.ai integration
    ├── getlate/GetLateService  # Social media API wrapper
    ├── devto/DevToService      # Dev.to publishing
    └── pipeline/               # Stage execution
```

## Key Concepts

### Posts
Markdown files with JSON metadata. Workflow: DRAFT → REVIEW → FINISHED.

### Pipelines
Multi-stage publishing workflows stored as `.pipeline.json`:
- `WEB_EXPORT` - Generate HTML with verification codes
- `URL_VERIFY` - Check URL is live before social publishing
- `GETLATE` - Publish to social platforms via GetLate API
- `DEV_TO` - Publish to Dev.to with canonical URL

### Platform Transforms
AI-generated, platform-specific versions of content. Each platform has default prompts optimized for its conventions (LinkedIn = professional, Twitter = punchy threads, Instagram = emoji-heavy, etc.).

### Publishing Profiles
Stored credentials for social accounts. Multiple profiles per platform supported.

## Common Tasks

### Adding a new AI provider
1. Implement `AiReviewService` interface in `service/ai/`
2. Register in `AiServiceFactory`
3. Add to `AiAgent` enum in model

### Adding a new platform
1. GetLate already supports 13+ platforms—check if it's covered
2. Add platform-specific default prompt in `PipelineStage.getDefaultPromptFor()`
3. Add UI components in `PipelineExecutionController`

### Modifying HTML export
Templates live alongside the export service. Edit Mustache templates or `WebExportService` for structural changes.

## Running

```bash
mvn clean javafx:run
```

## Building

```bash
mvn clean package
# Produces fat JAR in target/
```

## API Keys Required

- **Grok** (x.ai) - for AI transformations
- **GetLate** - for social media publishing
- **Dev.to** (optional) - for technical article publishing
