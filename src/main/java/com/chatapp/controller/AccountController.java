package com.chatapp.controller;

import com.chatapp.config.JwtUtil;
import com.chatapp.config.UserPrincipal;
import com.chatapp.dto.AddFriendDto;
import com.chatapp.dto.ApiResponse;
import com.chatapp.dto.LoginDto;
import com.chatapp.model.AppUser;
import com.chatapp.model.FriendsRequest;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.service.DiscordColdStorageService;
import com.chatapp.service.FriendRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.chatapp.dto.OnlineUserDto;
import com.chatapp.service.DiscordSyncService;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DiscordColdStorageService discordColdStorageService;
    private final FriendRequestService friendRequestService;
    private final DiscordSyncService discordSyncService;
    private final com.chatapp.websocket.SignalRChatWebSocketHandler signalRHandler;

    public AccountController(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            DiscordColdStorageService discordColdStorageService,
            FriendRequestService friendRequestService,
            DiscordSyncService discordSyncService,
            @org.springframework.context.annotation.Lazy com.chatapp.websocket.SignalRChatWebSocketHandler signalRHandler) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.discordColdStorageService = discordColdStorageService;
        this.friendRequestService = friendRequestService;
        this.discordSyncService = discordSyncService;
        this.signalRHandler = signalRHandler;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("userName") String userName,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            HttpServletRequest request) {

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("User exists."));
        }

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String portPart = (serverPort == 80 || serverPort == 443) ? "" : ":" + serverPort;
        String baseUrl = scheme + "://" + serverName + portPart;

        String profileImageUrl;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                profileImageUrl = discordColdStorageService.uploadMedia(profileImage, baseUrl);
            } catch (IOException e) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("Could not upload profile picture: " + e.getMessage()));
            }
        } else {
            String encodedName = java.net.URLEncoder.encode(fullName, java.nio.charset.StandardCharsets.UTF_8);
            profileImageUrl = "https://ui-avatars.com/api/?name=" + encodedName + "&background=6366f1&color=fff";
        }

        AppUser user = new AppUser(
                email,
                userName,
                fullName,
                profileImageUrl,
                passwordEncoder.encode(password)
        );

        userRepository.save(user);
        discordSyncService.backupUserToDiscord(user);

        return ResponseEntity.ok(ApiResponse.success("", "User created"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody LoginDto dto) {
        if (dto == null || dto.getEmail() == null || dto.getPassword() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid login details"));
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("User not found"));
        }

        AppUser user = userOpt.get();
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid password"));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUserName());
        return ResponseEntity.ok(ApiResponse.success(token, "Login successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AppUser>> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(401).body(ApiResponse.failure("Unauthorized"));
        }

        Optional<AppUser> userOpt = userRepository.findById(principal.getId());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUserName(principal.getName());
        }

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.failure("User not found"));
        }

        return ResponseEntity.ok(ApiResponse.success(userOpt.get(), "User fetched successfully!"));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<OnlineUserDto>>> getAllUsers() {
        List<AppUser> all = userRepository.findAll();
        List<OnlineUserDto> list = new ArrayList<>();
        for (AppUser u : all) {
            OnlineUserDto dto = new OnlineUserDto();
            dto.setId(u.getId());
            dto.setUserName(u.getUserName());
            dto.setFullName(u.getFullName());
            dto.setProfilePicture(u.getProfileImage());
            dto.setEmail(u.getEmail());
            dto.setFriendsId(u.getFriendsId());
            list.add(dto);
        }
        return ResponseEntity.ok(ApiResponse.success(list, "Users fetched successfully!"));
    }

    @PostMapping("/sendfriendrequest")
    public ResponseEntity<ApiResponse<String>> sendFriendRequest(@RequestBody AddFriendDto dto) {
        if (dto == null || dto.getSenderId() == null || dto.getReceiverId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Id's details"));
        }

        boolean sent = friendRequestService.sendFriendRequest(dto.getSenderId(), dto.getReceiverId());
        if (sent) {
            String senderName = userRepository.findById(dto.getSenderId()).map(AppUser::getFullName).orElse("Someone");
            signalRHandler.sendToUser(dto.getReceiverId(), "ReceiveFriendRequest", java.util.Map.of(
                    "senderId", dto.getSenderId(),
                    "senderName", senderName,
                    "receiverId", dto.getReceiverId()
            ));
            return ResponseEntity.ok(ApiResponse.success("", "Friend request sent"));
        }
        return ResponseEntity.ok(ApiResponse.success("", "Friend request already sent or already friends"));
    }

    @PostMapping("/acceptrequest")
    public ResponseEntity<ApiResponse<String>> acceptRequest(@RequestBody AddFriendDto dto) {
        if (dto == null || dto.getSenderId() == null || dto.getReceiverId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Id's details"));
        }

        boolean accepted = friendRequestService.acceptFriendRequest(dto.getSenderId(), dto.getReceiverId());
        if (accepted) {
            discordSyncService.backupFriendshipToDiscord(dto.getSenderId(), dto.getReceiverId());
            signalRHandler.sendToUser(dto.getSenderId(), "FriendRequestAccepted", java.util.Map.of(
                    "friendId", dto.getReceiverId(),
                    "senderId", dto.getSenderId()
            ));
            signalRHandler.sendToUser(dto.getReceiverId(), "FriendRequestAccepted", java.util.Map.of(
                    "friendId", dto.getSenderId(),
                    "senderId", dto.getSenderId()
            ));
            signalRHandler.broadcastOnlineUsersToAll();
        }
        return ResponseEntity.ok(ApiResponse.success("", "Friend request accepted"));
    }

    @PostMapping({"/rejectrequest", "/rejectfriendrequest"})
    public ResponseEntity<ApiResponse<String>> rejectRequest(@RequestBody AddFriendDto dto) {
        if (dto == null || dto.getSenderId() == null || dto.getReceiverId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Id's details"));
        }

        friendRequestService.rejectFriendRequest(dto.getSenderId(), dto.getReceiverId());
        signalRHandler.sendToUser(dto.getSenderId(), "FriendRequestRejected", java.util.Map.of(
                "receiverId", dto.getReceiverId()
        ));
        return ResponseEntity.ok(ApiResponse.success("", "Friend request rejected"));
    }

    @GetMapping({"/firendsrequestsforme", "/friendrequestsforme"})
    public ResponseEntity<ApiResponse<List<FriendsRequest>>> getRequestsForMe(@RequestParam("receiverId") String receiverId) {
        List<FriendsRequest> requests = friendRequestService.getRequestsForMe(receiverId);
        return ResponseEntity.ok(ApiResponse.success(requests, "User fetched successfully!"));
    }

    @GetMapping({"/firendsrequestssendbyme", "/friendrequestssendbyme"})
    public ResponseEntity<ApiResponse<List<FriendsRequest>>> getRequestsSendByMe(@RequestParam("senderId") String senderId) {
        List<FriendsRequest> requests = friendRequestService.getRequestsSendByMe(senderId);
        return ResponseEntity.ok(ApiResponse.success(requests, "User fetched successfully!"));
    }

    @PostMapping("/sync-discord")
    public ResponseEntity<ApiResponse<String>> syncDiscord() {
        discordSyncService.syncFromDiscordOnStartup();
        signalRHandler.broadcastOnlineUsersToAll();
        return ResponseEntity.ok(ApiResponse.success("Synced", "Discord cloud sync completed"));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("File is required."));
        }

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String portPart = (serverPort == 80 || serverPort == 443) ? "" : ":" + serverPort;
        String baseUrl = scheme + "://" + serverName + portPart;

        try {
            String url = discordColdStorageService.uploadMedia(file, baseUrl);
            return ResponseEntity.ok(ApiResponse.success(url, "Media uploaded successfully to storage"));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping({"/admin/delete-all-users", "/delete-all-users"})
    public ResponseEntity<ApiResponse<String>> deleteAllUsers() {
        userRepository.deleteAll();
        signalRHandler.broadcastOnlineUsersToAll();
        return ResponseEntity.ok(ApiResponse.success("All users deleted", "Users wiped successfully"));
    }
}
