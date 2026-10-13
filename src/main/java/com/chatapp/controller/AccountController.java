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
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DiscordColdStorageService discordColdStorageService;
    private final FriendRequestService friendRequestService;

    public AccountController(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            DiscordColdStorageService discordColdStorageService,
            FriendRequestService friendRequestService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.discordColdStorageService = discordColdStorageService;
        this.friendRequestService = friendRequestService;
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

    @PostMapping("/sendfriendrequest")
    public ResponseEntity<ApiResponse<String>> sendFriendRequest(@RequestBody AddFriendDto dto) {
        if (dto == null || dto.getSenderId() == null || dto.getReceiverId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Id's details"));
        }

        friendRequestService.sendFriendRequest(dto.getSenderId(), dto.getReceiverId());
        return ResponseEntity.ok(ApiResponse.success("", "sended"));
    }

    @PostMapping("/acceptrequest")
    public ResponseEntity<ApiResponse<String>> acceptRequest(@RequestBody AddFriendDto dto) {
        if (dto == null || dto.getSenderId() == null || dto.getReceiverId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Id's details"));
        }

        friendRequestService.acceptFriendRequest(dto.getSenderId(), dto.getReceiverId());
        return ResponseEntity.ok(ApiResponse.success("", "sended"));
    }

    @GetMapping("/firendsrequestsforme")
    public ResponseEntity<ApiResponse<List<FriendsRequest>>> getRequestsForMe(@RequestParam("receiverId") String receiverId) {
        List<FriendsRequest> requests = friendRequestService.getRequestsForMe(receiverId);
        return ResponseEntity.ok(ApiResponse.success(requests, "User fetched successfully!"));
    }

    @GetMapping("/firendsrequestssendbyme")
    public ResponseEntity<ApiResponse<List<FriendsRequest>>> getRequestsSendByMe(@RequestParam("senderId") String senderId) {
        List<FriendsRequest> requests = friendRequestService.getRequestsSendByMe(senderId);
        return ResponseEntity.ok(ApiResponse.success(requests, "User fetched successfully!"));
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
}
