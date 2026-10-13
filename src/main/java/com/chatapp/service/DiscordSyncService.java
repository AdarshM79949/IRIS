package com.chatapp.service;

import com.chatapp.model.AppUser;
import com.chatapp.model.ChatGroup;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.repository.ChatGroupRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class DiscordSyncService {

    private static final Logger log = LoggerFactory.getLogger(DiscordSyncService.class);

    private static final String USER_PREFIX = "[IRIS_DATA_SYNC_USER] ";
    private static final String GROUP_PREFIX = "[IRIS_DATA_SYNC_GROUP] ";
    private static final String FRIEND_PREFIX = "[IRIS_DATA_SYNC_FRIEND] ";

    @Value("${app.discord.bot-token:${DISCORD_BOT_TOKEN:${BOT1_TOKEN:}}}")
    private String botToken;

    @Value("${app.discord.channel-id:${DISCORD_CHANNEL_ID:1559546380077367429}}")
    private String channelId;

    private final AppUserRepository userRepository;
    private final ChatGroupRepository groupRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DiscordSyncService(AppUserRepository userRepository, ChatGroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private boolean isConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(channelId);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bot " + botToken.trim());
        headers.set("User-Agent", "DiscordBot (IRIS, 1.0)");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Called on application startup: loads users, passwords, and groups from Discord cloud storage.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncFromDiscordOnStartup() {
        if (!isConfigured()) {
            log.info("Discord cloud persistence not configured (missing token or channel ID)");
            return;
        }

        try {
            log.info("Syncing cloud users and data from Discord channel {}", channelId);
            String url = "https://discord.com/api/v10/channels/" + channelId + "/messages?limit=100";
            HttpEntity<Void> request = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Could not fetch messages from Discord: {}", response.getStatusCode());
                return;
            }

            JsonNode messages = objectMapper.readTree(response.getBody());
            if (!messages.isArray()) return;

            // Process in reverse chronological order (oldest to newest)
            List<JsonNode> msgList = new ArrayList<>();
            messages.forEach(msgList::add);
            Collections.reverse(msgList);

            int restoredUsers = 0;
            int restoredGroups = 0;

            for (JsonNode msgNode : msgList) {
                String content = msgNode.has("content") ? msgNode.get("content").asText() : "";
                if (content.startsWith(USER_PREFIX)) {
                    String json = content.substring(USER_PREFIX.length()).trim();
                    if (restoreUserFromJson(json)) {
                        restoredUsers++;
                    }
                } else if (content.startsWith(GROUP_PREFIX)) {
                    String json = content.substring(GROUP_PREFIX.length()).trim();
                    if (restoreGroupFromJson(json)) {
                        restoredGroups++;
                    }
                } else if (content.startsWith(FRIEND_PREFIX)) {
                    String json = content.substring(FRIEND_PREFIX.length()).trim();
                    restoreFriendFromJson(json);
                }
            }

            log.info("Discord cloud restore completed: {} users, {} groups synced", restoredUsers, restoredGroups);
        } catch (Exception e) {
            log.error("Failed to sync cloud state from Discord: {}", e.getMessage());
        }
    }

    private boolean restoreUserFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String userId = node.get("id").asText();
            String email = node.get("email").asText();
            String userName = node.get("userName").asText();

            Optional<AppUser> existing = userRepository.findById(userId);
            if (existing.isEmpty()) {
                existing = userRepository.findByEmail(email);
            }
            if (existing.isEmpty()) {
                existing = userRepository.findByUserName(userName);
            }

            AppUser user = existing.orElseGet(AppUser::new);
            user.setId(userId);
            user.setFullName(node.get("fullName").asText());
            user.setUserName(userName);
            user.setEmail(email);
            if (node.has("passwordHash")) {
                user.setPassword(node.get("passwordHash").asText());
            }
            if (node.has("profileImage") && !node.get("profileImage").isNull()) {
                user.setProfileImage(node.get("profileImage").asText());
            }

            if (node.has("friendsId") && node.get("friendsId").isArray()) {
                List<String> friends = new ArrayList<>(user.getFriendsId());
                node.get("friendsId").forEach(f -> {
                    String fid = f.asText();
                    if (!friends.contains(fid)) friends.add(fid);
                });
                user.setFriendsId(friends);
            }

            userRepository.save(user);
            return true;
        } catch (Exception e) {
            log.warn("Failed to restore user from Discord JSON: {}", e.getMessage());
            return false;
        }
    }

    private boolean restoreGroupFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String groupId = node.get("id").asText();
            String name = node.get("name").asText();
            String creatorId = node.has("creatorId") ? node.get("creatorId").asText() : null;

            ChatGroup group = groupRepository.findById(groupId).orElseGet(() -> {
                ChatGroup g = new ChatGroup();
                g.setId(groupId);
                return g;
            });

            group.setName(name);
            group.setCreatorId(creatorId);
            if (node.has("memberIds") && node.get("memberIds").isArray()) {
                List<String> memberList = new ArrayList<>();
                node.get("memberIds").forEach(m -> memberList.add(m.asText()));
                group.setMemberIds(memberList);
            }

            groupRepository.save(group);
            return true;
        } catch (Exception e) {
            log.warn("Failed to restore group from Discord JSON: {}", e.getMessage());
            return false;
        }
    }

    private void restoreFriendFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String sId = node.get("senderId").asText();
            String rId = node.get("receiverId").asText();

            userRepository.findById(sId).ifPresent(s -> {
                if (!s.getFriendsId().contains(rId)) {
                    s.getFriendsId().add(rId);
                    userRepository.save(s);
                }
            });
            userRepository.findById(rId).ifPresent(r -> {
                if (!r.getFriendsId().contains(sId)) {
                    r.getFriendsId().add(sId);
                    userRepository.save(r);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to restore friendship from Discord JSON: {}", e.getMessage());
        }
    }

    public void backupUserToDiscord(AppUser user) {
        if (!isConfigured() || user == null) return;
        new Thread(() -> {
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("id", user.getId());
                map.put("fullName", user.getFullName());
                map.put("userName", user.getUserName());
                map.put("email", user.getEmail());
                map.put("passwordHash", user.getPassword());
                map.put("profileImage", user.getProfileImage());
                map.put("friendsId", user.getFriendsId());

                String payload = USER_PREFIX + objectMapper.writeValueAsString(map);
                postMessageToDiscord(payload);
            } catch (Exception e) {
                log.warn("Failed to backup user {} to Discord: {}", user.getUserName(), e.getMessage());
            }
        }).start();
    }

    public void backupGroupToDiscord(ChatGroup group) {
        if (!isConfigured() || group == null) return;
        new Thread(() -> {
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("id", group.getId());
                map.put("name", group.getName());
                map.put("creatorId", group.getCreatorId());
                map.put("memberIds", group.getMemberIds());

                String payload = GROUP_PREFIX + objectMapper.writeValueAsString(map);
                postMessageToDiscord(payload);
            } catch (Exception e) {
                log.warn("Failed to backup group {} to Discord: {}", group.getName(), e.getMessage());
            }
        }).start();
    }

    public void backupFriendshipToDiscord(String senderId, String receiverId) {
        if (!isConfigured()) return;
        new Thread(() -> {
            try {
                Map<String, String> map = Map.of("senderId", senderId, "receiverId", receiverId);
                String payload = FRIEND_PREFIX + objectMapper.writeValueAsString(map);
                postMessageToDiscord(payload);
            } catch (Exception e) {
                log.warn("Failed to backup friendship to Discord: {}", e.getMessage());
            }
        }).start();
    }

    private void postMessageToDiscord(String text) {
        try {
            String url = "https://discord.com/api/v10/channels/" + channelId + "/messages";
            Map<String, String> body = Map.of("content", text);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders());
            restTemplate.postForEntity(url, request, String.class);
            log.info("Persisted cloud record to Discord storage channel {}", channelId);
        } catch (Exception e) {
            log.warn("Could not post to Discord channel: {}", e.getMessage());
        }
    }
}
