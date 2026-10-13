package com.chatapp.dto;

import java.util.ArrayList;
import java.util.List;

public class AiDrawingRequestDto {

    private String imageData;
    private String prompt;
    private String focusArea; // "GENERAL", "COMPOSITION", "TECHNIQUE", "COLOR", "DIAGRAM"
    private int width;
    private int height;
    private int strokeCount;
    private List<String> colorsUsed = new ArrayList<>();
    private List<String> toolsUsed = new ArrayList<>();

    public AiDrawingRequestDto() {
    }

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getFocusArea() {
        return focusArea != null ? focusArea : "GENERAL";
    }

    public void setFocusArea(String focusArea) {
        this.focusArea = focusArea;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getStrokeCount() {
        return strokeCount;
    }

    public void setStrokeCount(int strokeCount) {
        this.strokeCount = strokeCount;
    }

    public List<String> getColorsUsed() {
        return colorsUsed;
    }

    public void setColorsUsed(List<String> colorsUsed) {
        this.colorsUsed = colorsUsed != null ? colorsUsed : new ArrayList<>();
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed != null ? toolsUsed : new ArrayList<>();
    }
}
