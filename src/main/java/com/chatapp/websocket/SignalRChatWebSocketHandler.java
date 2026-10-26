package com.chatapp.websocket;

import com.chatapp.config.JwtUtil;
import com.chatapp.dto.MessageResponseDto;
import com.chatapp.dto.OnlineUserDto;
import com.chatapp.model.AppUser;
import com.chatapp.model.ChatGroup;
import com.chatapp.model.Message;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalRChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalRChatWebSocketHandler.class);
    private static final String RECORD_SEPARATOR = "\u001e";

    public static final ConcurrentHashMap<String, OnlineUserDto> onlineUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> connectionSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUserName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUserId = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final AppUserRepository userRepository;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public SignalRChatWebSocketHandler(
            JwtUtil jwtUtil,
            AppUserRepository userRepository,
            ChatService chatService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.info("WebSocket connection established: session ID {}", session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();
        String[] frames = payload.split(RECORD_SEPARATOR);

        for (String frame : frames) {
            String trimmed = frame.trim();
            if (trimmed.isEmpty()) continue;

            JsonNode node = objectMapper.readTree(trimmed);

            // SignalR Handshake Request
            if (node.has("protocol")) {
                session.sendMessage(new TextMessage("{}" + RECORD_SEPARATOR));
                onHandshakeCompleted(session);
                continue;
            }

            // SignalR Ping / Keep-Alive message
            if (node.has("type") && node.get("type").asInt() == 6) {
                session.sendMessage(new TextMessage("{\"type\":6}" + RECORD_SEPARATOR));
                continue;
            }

            // SignalR Invocation message (type == 1)
            if (node.has("type") && node.get("type").asInt() == 1) {
                handleInvocation(session, node);
            }
        }
    }

    private void onHandshakeCompleted(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        String userName = null;
        String userId = null;

        if (token != null && !token.isBlank() && !"undefined".equalsIgnoreCase(token) && !"null".equalsIgnoreCase(token)) {
            String validationError = jwtUtil.getTokenValidationError(token);
            if (validationError == null) {
                userId = jwtUtil.getUserIdFromToken(token);
                userName = jwtUtil.getUserNameFromToken(token);
            } else {
                log.warn("SignalR session {} JWT validation rejected: {}", session.getId(), validationError);
            }
        } else {
            log.warn("SignalR session {} attempted connection without token", session.getId());
        }

        if (userName == null && userId != null) {
            Optional<AppUser> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                userName = userOpt.get().getUserName();
            }
        }

        if (userName == null) {
            log.warn("Unauthenticated SignalR connection attempt on session {} - closing connection", session.getId());
            try {
                session.close(new CloseStatus(4001, "Unauthorized: Invalid or expired token"));
            } catch (Exception ignored) {}
            return;
        }

        Optional<AppUser> currentUserOpt = userRepository.findByUserName(userName);
        if (currentUserOpt.isEmpty() && userId != null) {
            currentUserOpt = userRepository.findById(userId);
        }

        if (currentUserOpt.isEmpty()) {
            log.warn("User {} not found in database for session {} - closing connection", userName, session.getId());
            try {
                session.close(new CloseStatus(4001, "User not found"));
            } catch (Exception ignored) {}
            return;
        }

        AppUser currentUser = currentUserOpt.get();
        userId = currentUser.getId();
        userName = currentUser.getUserName();

        String connectionId = session.getId();
        connectionSessions.put(connectionId, session);
        sessionToUserName.put(connectionId, userName);
        sessionToUserId.put(connectionId, userId);

        boolean wasAlreadyOnline = onlineUsers.containsKey(userName);

        OnlineUserDto userDto = new OnlineUserDto();
        userDto.setId(userId);
        userDto.setConnectionId(connectionId);
        userDto.setUserName(userName);
        userDto.setFullName(currentUser.getFullName());
        userDto.setProfilePicture(currentUser.getProfileImage());
        userDto.setEmail(currentUser.getEmail());
        userDto.setOnline(true);
        userDto.setFriendsId(currentUser.getFriendsId());

        onlineUsers.put(userName, userDto);

        // Notify other clients if user wasn't already online
        if (!wasAlreadyOnline) {
            broadcastToOthers(session, "Notify", currentUser);
        }

        // If query param senderId exists, load messages
        Map<String, String> queryParams = getQueryParams(session.getUri());
        String senderId = queryParams.get("senderId");
        if (senderId != null && !senderId.isEmpty()) {
            loadMessagesForSession(session, userId, senderId, 1);
        }

        // Broadcast updated online users to all clients
        broadcastOnlineUsersToAll();
    }

    private void handleInvocation(WebSocketSession session, JsonNode node) throws Exception {
        String target = node.has("target") ? node.get("target").asText() : "";
        JsonNode args = node.get("arguments");
        String invocationId = node.has("invocationId") ? node.get("invocationId").asText() : null;

        String currentUserName = sessionToUserName.get(session.getId());
        String currentUserId = sessionToUserId.get(session.getId());

        if (currentUserName == null && currentUserId == null) {
            return;
        }

        switch (target) {
            case "LoadMessages" -> {
                if (args != null && args.size() > 0) {
                    String recipientId = args.get(0).asText();
                    int page = args.size() > 1 ? args.get(1).asInt(1) : 1;
                    loadMessagesForSession(session, currentUserId, recipientId, page);
                }
            }
            case "SendMessage" -> {
                if (args != null && args.size() > 0) {
                    JsonNode msgNode = args.get(0);
                    String receiverId = msgNode.has("receiverId") ? msgNode.get("receiverId").asText() : "";
                    String content = msgNode.has("content") ? msgNode.get("content").asText() : "";

                    Message savedMessage = chatService.saveMessage(currentUserId, receiverId, content);

                    // Send to recipient if connected
                    sendToUser(receiverId, "ReceiveNewMessage", savedMessage);

                    // Complete invocation for sender
                    if (invocationId != null) {
                        sendCompletion(session, invocationId);
                    }
                }
            }
            case "NotifyTyping" -> {
                if (args != null && args.size() > 0) {
                    String recipientUserName = args.get(0).asText();
                    sendToUser(recipientUserName, "NotifyTypingToUser", currentUserName);
                }
            }
            case "SendCanvasDraw" -> {
                if (args != null && args.size() > 0) {
                    JsonNode drawData = args.get(0);
                    String receiverId = drawData.has("receiverId") ? drawData.get("receiverId").asText() : "";
                    if (!receiverId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(drawData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToUser(receiverId, "ReceiveCanvasDraw", payload);
                    }
                }
            }
            case "SendCanvasClear" -> {
                if (args != null && args.size() > 0) {
                    JsonNode clearData = args.get(0);
                    String receiverId = clearData.has("receiverId") ? clearData.get("receiverId").asText() : "";
                    if (!receiverId.isEmpty()) {
                        sendToUser(receiverId, "ReceiveCanvasClear", Map.of(
                            "senderId", currentUserId,
                            "senderName", currentUserName
                        ));
                    }
                }
            }
            case "SendCanvasCursor" -> {
                if (args != null && args.size() > 0) {
                    JsonNode cursorData = args.get(0);
                    String receiverId = cursorData.has("receiverId") ? cursorData.get("receiverId").asText() : "";
                    if (!receiverId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(cursorData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToUser(receiverId, "ReceiveCanvasCursor", payload);
                    }
                }
            }
            case "ToggleCanvas" -> {
                if (args != null && args.size() > 0) {
                    JsonNode toggleData = args.get(0);
                    String receiverId = toggleData.has("receiverId") ? toggleData.get("receiverId").asText() : "";
                    if (!receiverId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(toggleData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToUser(receiverId, "ReceiveToggleCanvas", payload);
                    }
                }
            }
            case "LoadGroupMessages" -> {
                if (args != null && args.size() > 0) {
                    String groupId = args.get(0).asText();
                    int page = args.size() > 1 ? args.get(1).asInt(1) : 1;
                    loadGroupMessagesForSession(session, groupId, page);
                }
            }
            case "SendGroupMessage" -> {
                if (args != null && args.size() > 0) {
                    JsonNode msgNode = args.get(0);
                    String groupId = msgNode.has("groupId") ? msgNode.get("groupId").asText() : "";
                    String content = msgNode.has("content") ? msgNode.get("content").asText() : "";
                    if (!groupId.isEmpty() && !content.isEmpty()) {
                        Message savedMessage = chatService.saveGroupMessage(currentUserId, groupId, content);
                        sendToGroup(groupId, "ReceiveNewGroupMessage", savedMessage);
                        if (invocationId != null) {
                            sendCompletion(session, invocationId);
                        }
                    }
                }
            }
            case "SendGroupCanvasDraw" -> {
                if (args != null && args.size() > 0) {
                    JsonNode drawData = args.get(0);
                    String groupId = drawData.has("groupId") ? drawData.get("groupId").asText() : "";
                    if (!groupId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(drawData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToGroupExcept(groupId, session.getId(), "ReceiveGroupCanvasDraw", payload);
                    }
                }
            }
            case "SendGroupCanvasClear" -> {
                if (args != null && args.size() > 0) {
                    JsonNode clearData = args.get(0);
                    String groupId = clearData.has("groupId") ? clearData.get("groupId").asText() : "";
                    if (!groupId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(clearData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToGroupExcept(groupId, session.getId(), "ReceiveGroupCanvasClear", payload);
                    }
                }
            }
            case "SendGroupCanvasCursor" -> {
                if (args != null && args.size() > 0) {
                    JsonNode cursorData = args.get(0);
                    String groupId = cursorData.has("groupId") ? cursorData.get("groupId").asText() : "";
                    if (!groupId.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.convertValue(cursorData, Map.class);
                        payload.put("senderId", currentUserId);
                        payload.put("senderName", currentUserName);
                        sendToGroupExcept(groupId, session.getId(), "ReceiveGroupCanvasCursor", payload);
                    }
                }
            }
            case "ToggleGroupCanvas" -> {
                if (args != null && args.size() > 0) {
                    JsonNode toggleData = args.get(0);
                    String groupId = toggleData.has("groupId") ? toggleData.get("groupId").asText() : "";
                    if (!groupId.isEmpty()) {
                        sendToGroupExcept(groupId, session.getId(), "ReceiveToggleGroupCanvas", toggleData);
                    }
                }
            }
            default -> log.debug("Unknown invocation target: {}", target);
        }
    }

    private void loadMessagesForSession(WebSocketSession session, String currentUserId, String recipientId, int page) throws IOException {
        List<MessageResponseDto> messages = chatService.loadMessages(currentUserId, recipientId, page, 100);
        sendHubMessage(session, "ReceiveMessageList", List.of(messages));
    }

    private void loadGroupMessagesForSession(WebSocketSession session, String groupId, int page) throws IOException {
        List<MessageResponseDto> messages = chatService.loadGroupMessages(groupId, page, 100);
        sendHubMessage(session, "ReceiveMessageList", List.of(messages));
    }

    private void sendToGroup(String groupId, String target, Object payload) {
        Optional<ChatGroup> groupOpt = chatService.getGroup(groupId);
        if (groupOpt.isEmpty()) return;
        List<String> memberIds = groupOpt.get().getMemberIds();
        if (memberIds == null || memberIds.isEmpty()) return;

        Set<String> memberSet = new HashSet<>(memberIds);
        for (Map.Entry<String, WebSocketSession> entry : connectionSessions.entrySet()) {
            String connId = entry.getKey();
            WebSocketSession session = entry.getValue();
            String uId = sessionToUserId.get(connId);

            if (session.isOpen() && uId != null && memberSet.contains(uId)) {
                try {
                    sendHubMessage(session, target, List.of(payload));
                } catch (IOException e) {
                    log.error("Error sending to group member {} on session {}", uId, connId, e);
                }
            }
        }
    }

    private void sendToGroupExcept(String groupId, String excludeConnectionId, String target, Object payload) {
        Optional<ChatGroup> groupOpt = chatService.getGroup(groupId);
        if (groupOpt.isEmpty()) return;
        List<String> memberIds = groupOpt.get().getMemberIds();
        if (memberIds == null || memberIds.isEmpty()) return;

        Set<String> memberSet = new HashSet<>(memberIds);
        for (Map.Entry<String, WebSocketSession> entry : connectionSessions.entrySet()) {
            String connId = entry.getKey();
            if (connId.equals(excludeConnectionId)) continue;
            WebSocketSession session = entry.getValue();
            String uId = sessionToUserId.get(connId);

            if (session.isOpen() && uId != null && memberSet.contains(uId)) {
                try {
                    sendHubMessage(session, target, List.of(payload));
                } catch (IOException e) {
                    log.error("Error sending to group member {} on session {}", uId, connId, e);
                }
            }
        }
    }

    public void broadcastOnlineUsersToAll() {
        for (Map.Entry<String, WebSocketSession> entry : connectionSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                String userId = sessionToUserId.get(session.getId());
                try {
                    List<OnlineUserDto> users = getAllUsersFor(userId);
                    sendHubMessage(session, "OnlineUsers", List.of(users));
                } catch (IOException e) {
                    log.error("Error sending online users to session {}", session.getId(), e);
                }
            }
        }
    }

    public List<OnlineUserDto> getAllUsersFor(String currentUserId) {
        List<AppUser> allUsers = userRepository.findAll();
        List<OnlineUserDto> dtoList = new ArrayList<>();

        for (AppUser u : allUsers) {
            OnlineUserDto dto = new OnlineUserDto();
            dto.setId(u.getId());
            dto.setUserName(u.getUserName());
            dto.setFullName(u.getFullName());
            dto.setProfilePicture(u.getProfileImage());
            dto.setEmail(u.getEmail());
            dto.setOnline(onlineUsers.containsKey(u.getUserName()));
            dto.setFriendsId(u.getFriendsId());

            if (currentUserId != null) {
                dto.setUnreadCount(chatService.getUnreadCount(currentUserId, u.getId()));
            } else {
                dto.setUnreadCount(0);
            }
            dtoList.add(dto);
        }

        dtoList.sort((a, b) -> Boolean.compare(b.isOnline(), a.isOnline()));
        return dtoList;
    }

    private void broadcastToOthers(WebSocketSession currentSession, String target, Object payload) {
        for (Map.Entry<String, WebSocketSession> entry : connectionSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen() && !session.getId().equals(currentSession.getId())) {
                try {
                    sendHubMessage(session, target, List.of(payload));
                } catch (IOException e) {
                    log.error("Error broadcasting to session {}", session.getId(), e);
                }
            }
        }
    }

    public void sendToUser(String userIdOrName, String target, Object payload) {
        for (Map.Entry<String, WebSocketSession> entry : connectionSessions.entrySet()) {
            String connId = entry.getKey();
            WebSocketSession session = entry.getValue();
            String uId = sessionToUserId.get(connId);
            String uName = sessionToUserName.get(connId);

            if (session.isOpen() && (userIdOrName.equals(uId) || userIdOrName.equalsIgnoreCase(uName))) {
                try {
                    sendHubMessage(session, target, List.of(payload));
                } catch (IOException e) {
                    log.error("Error sending to user {} on session {}", userIdOrName, connId, e);
                }
            }
        }
    }

    private void sendHubMessage(WebSocketSession session, String target, List<Object> arguments) throws IOException {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", 1);
        msg.put("target", target);
        msg.put("arguments", arguments);

        if (!session.isOpen()) return;
        String json = objectMapper.writeValueAsString(msg) + RECORD_SEPARATOR;
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    log.debug("Session closed while sending message: {}", e.getMessage());
                }
            }
        }
    }

    private void sendCompletion(WebSocketSession session, String invocationId) throws IOException {
        if (!session.isOpen()) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", 3);
        msg.put("invocationId", invocationId);
        msg.put("result", null);

        String json = objectMapper.writeValueAsString(msg) + RECORD_SEPARATOR;
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    log.debug("Session closed while sending completion: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String connId = session.getId();
        connectionSessions.remove(connId);
        String userName = sessionToUserName.remove(connId);
        sessionToUserId.remove(connId);

        if (userName != null) {
            // Check if user still has other active connections
            boolean hasOtherSessions = sessionToUserName.containsValue(userName);
            if (!hasOtherSessions) {
                onlineUsers.remove(userName);
            }
        }

        broadcastOnlineUsersToAll();
        log.info("WebSocket connection closed for session: {}", connId);
    }

    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> query = getQueryParams(uri);
            String rawToken = query.get("access_token");
            if (rawToken == null || rawToken.isBlank()) {
                rawToken = query.get("token");
            }
            if (rawToken != null && !rawToken.isBlank()) {
                return cleanToken(rawToken);
            }
        }

        List<String> authHeaders = session.getHandshakeHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            return cleanToken(header);
        }

        return null;
    }

    private String cleanToken(String token) {
        if (token == null) return null;
        token = token.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        if (token.contains("%")) {
            try {
                token = java.net.URLDecoder.decode(token, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        if (token.contains(" ")) {
            token = token.replace(" ", "+");
        }
        return token.trim();
    }

    private Map<String, String> getQueryParams(URI uri) {
        Map<String, String> queryPairs = new HashMap<>();
        if (uri == null) return queryPairs;
        String queryStr = uri.getRawQuery() != null ? uri.getRawQuery() : uri.getQuery();
        if (queryStr == null || queryStr.isBlank()) return queryPairs;
        String[] pairs = queryStr.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String val = pair.substring(idx + 1);
                if (!queryPairs.containsKey(key) && !val.isBlank()) {
                    queryPairs.put(key, val);
                }
            }
        }
        return queryPairs;
    }
}
