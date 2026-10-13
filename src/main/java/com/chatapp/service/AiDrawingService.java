package com.chatapp.service;

import com.chatapp.dto.AiDrawingRequestDto;
import com.chatapp.dto.AiDrawingResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class AiDrawingService {

    private static final Logger log = LoggerFactory.getLogger(AiDrawingService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.model:qwen/qwen3.8-27b}")
    private String groqModel;

    @Value("${app.ai.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${app.ai.openrouter.model:openrouter/free}")
    private String openrouterModel;

    public AiDrawingService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AiDrawingResponseDto analyzeDrawing(AiDrawingRequestDto request) {
        String prompt = buildPrompt(request);

        // 1. Try Groq first
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            try {
                String aiText = callGroq(prompt, request);
                if (aiText != null && !aiText.isBlank()) {
                    return parseAiResponse(aiText, "Groq (" + groqModel + ")", request);
                }
            } catch (Exception e) {
                log.warn("Groq AI analysis failed, trying OpenRouter fallback: {}", e.getMessage());
            }
        }

        // 2. Try OpenRouter fallback
        if (openrouterApiKey != null && !openrouterApiKey.isBlank()) {
            try {
                String aiText = callOpenRouter(prompt, request);
                if (aiText != null && !aiText.isBlank()) {
                    return parseAiResponse(aiText, "OpenRouter (" + openrouterModel + ")", request);
                }
            } catch (Exception e) {
                log.warn("OpenRouter AI analysis failed: {}", e.getMessage());
            }
        }

        // 3. Fallback to smart heuristic art mentor engine
        return generateHeuristicFeedback(request);
    }

    private String buildPrompt(AiDrawingRequestDto request) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert art director, drawing coach, and visual design AI mentor.\n");
        sb.append("Analyze this canvas drawing description and user question to help the artist understand their work and give clear, constructive, and actionable improvement steps.\n\n");

        sb.append("Canvas Context:\n");
        sb.append("- Canvas Size: ").append(request.getWidth() > 0 ? request.getWidth() : 800)
                .append("x").append(request.getHeight() > 0 ? request.getHeight() : 400).append(" px\n");

        if (request.getStrokeCount() > 0) {
            sb.append("- Estimated strokes/drawn elements: ").append(request.getStrokeCount()).append("\n");
        }
        if (request.getToolsUsed() != null && !request.getToolsUsed().isEmpty()) {
            sb.append("- Drawing tools used: ").append(String.join(", ", request.getToolsUsed())).append("\n");
        }
        if (request.getColorsUsed() != null && !request.getColorsUsed().isEmpty()) {
            sb.append("- Dominant colors in sketch: ").append(String.join(", ", request.getColorsUsed())).append("\n");
        }
        sb.append("- Analysis Focus Area: ").append(request.getFocusArea()).append("\n");

        if (request.getPrompt() != null && !request.getPrompt().trim().isEmpty()) {
            sb.append("- Artist Inquiry: \"").append(request.getPrompt().trim()).append("\"\n");
        }

        sb.append("\nPlease format your response cleanly in Markdown with the following structured sections:\n");
        sb.append("### 🎯 Interpretation & Understanding\n(A concise explanation of what this drawing communicates, its core concept, or diagram layout)\n\n");
        sb.append("### 📐 Composition & Visual Balance\n(Evaluate proportions, layout flow, negative space, and linework clarity)\n\n");
        sb.append("### 🛠️ Actionable Improvement Suggestions\n(Provide 3 to 5 clear, bulleted steps to refine linework, shading, spacing, and polish)\n\n");
        sb.append("### 🚀 Creative Next Steps\n(Inspiring ideas for what details, labels, or complementary visual elements to add next)\n");

        return sb.toString();
    }

    private String callGroq(String prompt, AiDrawingRequestDto request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", groqModel);
        body.put("temperature", 0.7);
        body.put("max_tokens", 850);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are an insightful, encouraging AI Drawing and Art Mentor. Always provide structured, practical advice."));
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
        } else {
            log.warn("Groq returned status code: {} body: {}", resp.statusCode(), resp.body());
        }
        return null;
    }

    private String callOpenRouter(String prompt, AiDrawingRequestDto request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", openrouterModel);
        body.put("temperature", 0.7);
        body.put("max_tokens", 850);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + openrouterApiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
        }
        return null;
    }

    private AiDrawingResponseDto parseAiResponse(String rawText, String provider, AiDrawingRequestDto request) {
        AiDrawingResponseDto dto = new AiDrawingResponseDto();
        dto.setSuccess(true);
        dto.setProvider(provider);
        dto.setTitle("AI Drawing Critique & Improvement Guide");
        dto.setFullMarkdown(rawText);

        // Extract key highlights
        List<String> suggestions = new ArrayList<>();
        String[] lines = rawText.split("\n");
        boolean inSuggestions = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("Actionable") || trimmed.contains("Suggestions") || trimmed.contains("Improvements")) {
                inSuggestions = true;
                continue;
            }
            if (inSuggestions && trimmed.startsWith("###")) {
                inSuggestions = false;
            }
            if (inSuggestions && (trimmed.startsWith("*") || trimmed.startsWith("-") || trimmed.matches("^\\d+\\..*"))) {
                String clean = trimmed.replaceAll("^[\\*\\-\\d\\.\\s]+", "").replaceAll("\\*\\*", "").trim();
                if (!clean.isEmpty()) {
                    suggestions.add(clean);
                }
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Standardize line weights for foreground elements vs secondary details.");
            suggestions.add("Increase contrast on key focal points to direct viewer attention.");
            suggestions.add("Utilize negative space to prevent visual crowding in the composition.");
        }

        dto.setSuggestions(suggestions);
        return dto;
    }

    private AiDrawingResponseDto generateHeuristicFeedback(AiDrawingRequestDto request) {
        AiDrawingResponseDto dto = new AiDrawingResponseDto();
        dto.setSuccess(true);
        dto.setProvider("AI Drawing Engine (Offline Heuristics)");
        dto.setTitle("Visual Design & Drawing Improvement Analysis");

        List<String> suggestions = new ArrayList<>();
        suggestions.add("Line Weight Hierarchy: Use thicker strokes (6-8px) for outlines/main nodes and lighter strokes (2-3px) for internal connectors and textures.");
        suggestions.add("Negative Space & Framing: Leave at least 15% breathing room around the edges of the canvas to avoid cramped borders.");
        suggestions.add("Color Contrast: Pair your white and bright accent strokes with dark backdrops to make focal points pop instantly.");
        suggestions.add("Geometric Precision: When drawing structural diagrams, combine geometric shapes (rectangles, circles) with clean connecting lines.");

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Add subtle drop shadows or secondary accent lines to introduce depth.");
        nextSteps.add("Label core components with brief captions to make your sketch self-explanatory.");
        nextSteps.add("Export or share the drawing directly with your collaborators in the chat.");

        dto.setSuggestions(suggestions);
        dto.setNextSteps(nextSteps);

        StringBuilder md = new StringBuilder();
        md.append("### 🎯 Interpretation & Understanding\n");
        md.append("Your canvas features an active sketch with ").append(request.getStrokeCount() > 0 ? request.getStrokeCount() : "several")
                .append(" visual strokes using tools like ").append(request.getToolsUsed().isEmpty() ? "Brush and Line" : String.join(", ", request.getToolsUsed()))
                .append(". The layout forms an expressive conceptual composition.\n\n");

        md.append("### 📐 Composition & Visual Balance\n");
        md.append("The arrangement establishes a defined center of interest. To maximize visual impact, ensure symmetrical alignment along the primary horizontal axis and maintain consistent padding along canvas perimeters.\n\n");

        md.append("### 🛠️ Actionable Improvement Suggestions\n");
        for (String s : suggestions) {
            md.append("- **").append(s).append("**\n");
        }
        md.append("\n");

        md.append("### 🚀 Creative Next Steps\n");
        for (String n : nextSteps) {
            md.append("- ").append(n).append("\n");
        }

        dto.setFullMarkdown(md.toString());
        return dto;
    }
}
