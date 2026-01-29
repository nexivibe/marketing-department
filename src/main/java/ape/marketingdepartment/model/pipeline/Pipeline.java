package ape.marketingdepartment.model.pipeline;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline configuration defining the stages for publishing a post.
 * Stored as .pipeline.json in the project root.
 */
public class Pipeline {
    private static final String PIPELINE_FILE = ".pipeline.json";

    private String id;
    private String name;
    private List<PipelineStage> stages;

    public Pipeline() {
        this.id = UUID.randomUUID().toString();
        this.name = "Default Pipeline";
        this.stages = new ArrayList<>();
    }

    public Pipeline(String name) {
        this();
        this.name = name;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PipelineStage> getStages() {
        return stages;
    }

    public void setStages(List<PipelineStage> stages) {
        this.stages = stages != null ? stages : new ArrayList<>();
    }

    public void addStage(PipelineStage stage) {
        stages.add(stage);
        reorderStages();
    }

    public void removeStage(PipelineStage stage) {
        stages.remove(stage);
        reorderStages();
    }

    public void removeStageById(String stageId) {
        stages.removeIf(s -> s.getId().equals(stageId));
        reorderStages();
    }

    private void reorderStages() {
        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).setOrder(i);
        }
    }

    /**
     * Get stages sorted by order.
     */
    public List<PipelineStage> getSortedStages() {
        return stages.stream()
                .sorted(Comparator.comparingInt(PipelineStage::getOrder))
                .toList();
    }

    /**
     * Get only enabled stages, sorted by order.
     */
    public List<PipelineStage> getEnabledStages() {
        return stages.stream()
                .filter(PipelineStage::isEnabled)
                .sorted(Comparator.comparingInt(PipelineStage::getOrder))
                .toList();
    }

    /**
     * Get gatekeeper stages (WEB_EXPORT, URL_VERIFY).
     */
    public List<PipelineStage> getGatekeeperStages() {
        return getSortedStages().stream()
                .filter(s -> s.getType() != null && s.getType().isGatekeeper())
                .toList();
    }

    /**
     * Get publishing stages (all non-gatekeeper stages: GETLATE, DEV_TO, etc.).
     */
    public List<PipelineStage> getSocialStages() {
        return getSortedStages().stream()
                .filter(s -> s.getType() != null && !s.getType().isGatekeeper())
                .toList();
    }

    /**
     * Find a stage by its ID.
     */
    public PipelineStage getStageById(String stageId) {
        return stages.stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if the pipeline has a stage of the given type.
     */
    public boolean hasStageOfType(PipelineStageType type) {
        return stages.stream().anyMatch(s -> s.getType() == type);
    }

    /**
     * Create a default pipeline with standard stages.
     */
    public static Pipeline createDefault() {
        Pipeline pipeline = new Pipeline("Default Pipeline");

        // Add gatekeeper stages
        PipelineStage webExport = new PipelineStage(PipelineStageType.WEB_EXPORT, 0);
        webExport.setId("web-export");
        pipeline.addStage(webExport);

        PipelineStage urlVerify = new PipelineStage(PipelineStageType.URL_VERIFY, 1);
        urlVerify.setId("url-verify");
        urlVerify.setStageSetting("requireCodeMatch", "true");
        pipeline.addStage(urlVerify);

        return pipeline;
    }

    /**
     * Load pipeline from project directory.
     */
    public static Pipeline load(Path projectPath) {
        Path pipelineFile = projectPath.resolve(PIPELINE_FILE);

        if (!Files.exists(pipelineFile)) {
            return createDefault();
        }

        try {
            String content = Files.readString(pipelineFile);
            return fromJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load pipeline: " + e.getMessage());
            return createDefault();
        }
    }

    /**
     * Save pipeline to project directory.
     */
    public void save(Path projectPath) throws IOException {
        Path pipelineFile = projectPath.resolve(PIPELINE_FILE);
        Files.writeString(pipelineFile, toJson());
    }

    public static Pipeline fromJson(String json) {
        Pipeline pipeline = new Pipeline();

        String id = JsonHelper.extractStringField(json, "id");
        if (id != null) {
            pipeline.id = id;
        }

        String name = JsonHelper.extractStringField(json, "name");
        if (name != null) {
            pipeline.name = name;
        }

        // Parse stages array
        List<String> stageJsons = JsonHelper.extractObjectArray(json, "stages");
        for (String stageJson : stageJsons) {
            pipeline.stages.add(PipelineStage.fromJson(stageJson));
        }

        return pipeline;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(JsonHelper.toJsonString(id)).append(",\n");
        sb.append("  \"name\": ").append(JsonHelper.toJsonString(name)).append(",\n");
        sb.append("  \"stages\": [\n");

        List<PipelineStage> sortedStages = getSortedStages();
        for (int i = 0; i < sortedStages.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(sortedStages.get(i).toJson());
        }

        sb.append("\n  ]\n");
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " (" + stages.size() + " stages)";
    }
}
