package com.chatapp.controller;

import com.chatapp.dto.ApiResponse;
import com.chatapp.dto.AiDrawingRequestDto;
import com.chatapp.dto.AiDrawingResponseDto;
import com.chatapp.service.AiDrawingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiDrawingService aiDrawingService;

    public AiController(AiDrawingService aiDrawingService) {
        this.aiDrawingService = aiDrawingService;
    }

    @PostMapping("/analyze-drawing")
    public ResponseEntity<ApiResponse<AiDrawingResponseDto>> analyzeDrawing(@RequestBody(required = false) AiDrawingRequestDto request) {
        if (request == null) {
            request = new AiDrawingRequestDto();
        }
        AiDrawingResponseDto response = aiDrawingService.analyzeDrawing(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Drawing analyzed successfully"));
    }
}
