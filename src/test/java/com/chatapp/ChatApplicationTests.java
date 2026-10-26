package com.chatapp;

import com.chatapp.config.JwtUtil;
import com.chatapp.dto.ApiResponse;
import com.chatapp.dto.LoginDto;
import com.chatapp.dto.MessageResponseDto;
import com.chatapp.model.AppUser;
import com.chatapp.model.FriendsRequest;
import com.chatapp.model.Message;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.repository.FriendsRequestRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.service.ChatService;
import com.chatapp.service.FriendRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("null")
class ChatApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private FriendsRequestRepository friendsRequestRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private com.chatapp.repository.ChatGroupRepository chatGroupRepository;

    @Autowired
    private com.chatapp.service.AiDrawingService aiDrawingService;

    @BeforeEach
    void setup() {
        messageRepository.deleteAll();
        chatGroupRepository.deleteAll();
        friendsRequestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertNotNull(userRepository);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testUserLoginAndJwtValidation() {
        AppUser user = new AppUser(
                "alice@example.com",
                "alice",
                "Alice Smith",
                "http://localhost:5000/uploads/avatar.png",
                passwordEncoder.encode("password123")
        );
        userRepository.save(user);

        LoginDto loginDto = new LoginDto("alice@example.com", "password123");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/account/login",
                loginDto,
                ApiResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());

        String token = response.getBody().getData().toString();
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("alice", jwtUtil.getUserNameFromToken(token));
    }

    @Test
    void testFriendRequestWorkflow() {
        AppUser user1 = new AppUser("user1@example.com", "user1", "User One", "p1.jpg", passwordEncoder.encode("pass"));
        AppUser user2 = new AppUser("user2@example.com", "user2", "User Two", "p2.jpg", passwordEncoder.encode("pass"));
        userRepository.save(user1);
        userRepository.save(user2);

        friendRequestService.sendFriendRequest(user1.getId(), user2.getId());

        List<FriendsRequest> requestsFor2 = friendRequestService.getRequestsForMe(user2.getId());
        assertEquals(1, requestsFor2.size());
        assertEquals(user1.getId(), requestsFor2.get(0).getSenderId());

        friendRequestService.acceptFriendRequest(user1.getId(), user2.getId());

        AppUser updated1 = userRepository.findById(user1.getId()).orElseThrow();
        AppUser updated2 = userRepository.findById(user2.getId()).orElseThrow();

        assertTrue(updated1.getFriendsId().contains(user2.getId()));
        assertTrue(updated2.getFriendsId().contains(user1.getId()));
        assertTrue(friendRequestService.getRequestsForMe(user2.getId()).isEmpty());
    }

    @Test
    void testChatMessages() {
        AppUser sender = new AppUser("s@example.com", "sender", "Sender", "s.jpg", passwordEncoder.encode("pass"));
        AppUser receiver = new AppUser("r@example.com", "receiver", "Receiver", "r.jpg", passwordEncoder.encode("pass"));
        userRepository.save(sender);
        userRepository.save(receiver);

        Message saved = chatService.saveMessage(sender.getId(), receiver.getId(), "Hello world!");
        assertNotNull(saved.getId());

        List<MessageResponseDto> messages = chatService.loadMessages(receiver.getId(), sender.getId(), 1, 50);
        assertEquals(1, messages.size());
        assertEquals("Hello world!", messages.get(0).getContent());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testMediaStorageUpload() {
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-image.png",
                "image/png",
                "dummy image content bytes".getBytes()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", mockFile.getResource());

        HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/account/upload",
                requestEntity,
                ApiResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().toString().contains("/uploads/") || response.getBody().getData().toString().contains("discord"));
    }

    @Test
    void testGroupCreationAndGroupChat() {
        AppUser creator = new AppUser("creator@example.com", "creator", "Group Creator", "c.jpg", passwordEncoder.encode("pass"));
        AppUser member1 = new AppUser("m1@example.com", "member1", "Member One", "m1.jpg", passwordEncoder.encode("pass"));
        AppUser member2 = new AppUser("m2@example.com", "member2", "Member Two", "m2.jpg", passwordEncoder.encode("pass"));
        userRepository.save(creator);
        userRepository.save(member1);
        userRepository.save(member2);

        // 1. Create group with creator and 2 members
        com.chatapp.model.ChatGroup group = chatService.createGroup(
                creator.getId(),
                "Creative Artists Club",
                List.of(member1.getId(), member2.getId()),
                "http://localhost:5000/uploads/group.png"
        );

        assertNotNull(group.getId());
        assertEquals("Creative Artists Club", group.getName());
        assertTrue(group.getMemberIds().contains(creator.getId()));
        assertTrue(group.getMemberIds().contains(member1.getId()));
        assertTrue(group.getMemberIds().contains(member2.getId()));

        // 2. Query user groups
        List<com.chatapp.model.ChatGroup> creatorGroups = chatService.getUserGroups(creator.getId());
        assertEquals(1, creatorGroups.size());
        assertEquals("Creative Artists Club", creatorGroups.get(0).getName());

        List<com.chatapp.model.ChatGroup> memberGroups = chatService.getUserGroups(member1.getId());
        assertEquals(1, memberGroups.size());

        // 3. Save group message
        Message groupMsg = chatService.saveGroupMessage(creator.getId(), group.getId(), "Welcome to our group canvas!");
        assertNotNull(groupMsg.getId());
        assertEquals(group.getId(), groupMsg.getGroupId());
        assertEquals("Group Creator", groupMsg.getSenderName());

        // 4. Load group messages
        List<MessageResponseDto> messages = chatService.loadGroupMessages(group.getId(), 1, 50);
        assertEquals(1, messages.size());
        assertEquals("Welcome to our group canvas!", messages.get(0).getContent());
        assertEquals("Group Creator", messages.get(0).getSenderName());
        assertEquals(group.getId(), messages.get(0).getGroupId());
    }

    @Test
    void testAiDrawingFeedback() {
        com.chatapp.dto.AiDrawingRequestDto req = new com.chatapp.dto.AiDrawingRequestDto();
        req.setWidth(800);
        req.setHeight(400);
        req.setStrokeCount(12);
        req.setToolsUsed(List.of("Brush", "Circle", "Line"));
        req.setColorsUsed(List.of("#ffffff", "#3b82f6"));
        req.setFocusArea("COMPOSITION");
        req.setPrompt("How can I make the central hub layout more balanced?");

        com.chatapp.dto.AiDrawingResponseDto response = aiDrawingService.analyzeDrawing(req);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getFullMarkdown());
        assertFalse(response.getFullMarkdown().isBlank());
        assertFalse(response.getSuggestions().isEmpty());
    }
}
