package com.chatapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/hubs/chat")
public class SignalRNegotiateController {

    @PostMapping("/negotiate")
    public ResponseEntity<Map<String, Object>> negotiate() {
        String connectionId = UUID.randomUUID().toString();
        Map<String, Object> response = new HashMap<>();
        response.put("negotiateVersion", 1);
        response.put("connectionId", connectionId);

        List<Map<String, Object>> transports = new ArrayList<>();
        Map<String, Object> wsTransport = new HashMap<>();
        wsTransport.put("transport", "WebSockets");
        wsTransport.put("transferFormats", List.of("Text", "Binary"));
        transports.add(wsTransport);

        response.put("availableTransports", transports);
        return ResponseEntity.ok(response);
    }
}
