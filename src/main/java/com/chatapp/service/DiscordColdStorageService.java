package com.chatapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class DiscordColdStorageService {

    private static final Logger log = LoggerFactory.getLogger(DiscordColdStorageService.class);

    @Value("${app.storage.discord.webhook-url:}")
    private String webhookUrl;

    @Value("${app.storage.discord.enabled:true}")
    private boolean discordEnabled;

    private final FileStorageService localFileStorageService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DiscordColdStorageService(FileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Uploads media file to Discord cold storage (via Discord Webhook).
     * If Discord webhook is not configured or fails, it falls back to local storage.
     *
     * @param file the MultipartFile to upload
     * @param baseUrl the application base URL (for local fallback)
     * @return the public URL of the uploaded media (Discord CDN or local URL)
     */
    public String uploadMedia(MultipartFile file, String baseUrl) throws IOException {
        if (discordEnabled && StringUtils.hasText(webhookUrl)) {
            try {
                String discordCdnUrl = uploadToDiscord(file);
                if (StringUtils.hasText(discordCdnUrl)) {
                    log.info("Successfully uploaded file to Discord cold storage: {}", discordCdnUrl);
                    return discordCdnUrl;
                }
            } catch (Exception e) {
                log.warn("Failed to upload to Discord cold storage, falling back to local storage: {}", e.getMessage());
            }
        }

        // Fallback to local file storage
        String localFileName = localFileStorageService.saveFile(file);
        return baseUrl + "/uploads/" + localFileName;
    }

    private String uploadToDiscord(MultipartFile file) throws IOException {
        String url = webhookUrl.trim();
        if (!url.contains("wait=true")) {
            url += (url.contains("?") ? "&" : "?") + "wait=true";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("User-Agent", "ChatApp-ColdStorage-Bot/1.0");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "media_attachment";
            }
        };

        body.add("file", resource);
        body.add("content", "📦 [Cold Storage Archive] Media attachment uploaded: " + file.getOriginalFilename());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode attachments = root.get("attachments");
            if (attachments != null && attachments.isArray() && attachments.size() > 0) {
                JsonNode firstAttachment = attachments.get(0);
                if (firstAttachment.has("url")) {
                    return firstAttachment.get("url").asText();
                }
            }
        }

        throw new IOException("Discord webhook returned non-200 or missing attachment details: " + response.getStatusCode());
    }

    public boolean isDiscordConfigured() {
        return discordEnabled && StringUtils.hasText(webhookUrl);
    }
}
