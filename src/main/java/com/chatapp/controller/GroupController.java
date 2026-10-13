package com.chatapp.controller;

import com.chatapp.config.UserPrincipal;
import com.chatapp.dto.ApiResponse;
import com.chatapp.dto.CreateGroupDto;
import com.chatapp.model.ChatGroup;
import com.chatapp.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/group")
public class GroupController {

    private final ChatService chatService;

    public GroupController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ChatGroup>> createGroup(
            @RequestBody CreateGroupDto dto,
            Authentication authentication) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Group name is required"));
        }

        String userId = "anonymous";
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            userId = principal.getId();
        }

        ChatGroup group = chatService.createGroup(userId, dto.getName(), dto.getMemberIds(), dto.getGroupImage());
        return ResponseEntity.ok(ApiResponse.success(group, "Group created successfully"));
    }

    @GetMapping("/my-groups")
    public ResponseEntity<ApiResponse<List<ChatGroup>>> getMyGroups(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(401).body(ApiResponse.failure("Unauthorized"));
        }

        List<ChatGroup> groups = chatService.getUserGroups(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(groups, "Groups fetched successfully"));
    }

    @PostMapping("/add-member")
    public ResponseEntity<ApiResponse<String>> addMember(@RequestBody Map<String, String> body) {
        String groupId = body.get("groupId");
        String userId = body.get("userId");
        if (groupId == null || userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("groupId and userId required"));
        }

        chatService.addMemberToGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("", "Member added"));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<ChatGroup>> getGroup(@PathVariable("groupId") String groupId) {
        return chatService.getGroup(groupId)
                .map(g -> ResponseEntity.ok(ApiResponse.success(g, "Group found")))
                .orElse(ResponseEntity.notFound().build());
    }
}
