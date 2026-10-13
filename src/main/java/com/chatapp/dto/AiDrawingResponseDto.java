package com.chatapp.dto;

import java.util.ArrayList;
import java.util.List;

public class AiDrawingResponseDto {

    private boolean success;
    private String title;
    private String interpretation;
    private String feedback;
    private List<String> suggestions = new ArrayList<>();
    private List<String> nextSteps = new ArrayList<>();
    private String fullMarkdown;
    private String provider;

    public AiDrawingResponseDto() {
        this.success = true;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public List<String> getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(List<String> nextSteps) {
        this.nextSteps = nextSteps != null ? nextSteps : new ArrayList<>();
    }

    public String getFullMarkdown() {
        return fullMarkdown;
    }

    public void setFullMarkdown(String fullMarkdown) {
        this.fullMarkdown = fullMarkdown;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
