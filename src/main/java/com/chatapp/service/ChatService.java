package com.chatapp.service;

import com.chatapp.dto.MessageResponseDto;
import com.chatapp.model.AppUser;
import com.chatapp.model.Message;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.model.ChatGroup;
import com.chatapp.repository.ChatGroupRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class ChatService {

    private final MessageRepository messageRepository;
    private final AppUserRepository userRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final DiscordSyncService discordSyncService;

    public ChatService(
            MessageRepository messageRepository,
            AppUserRepository userRepository,
            ChatGroupRepository chatGroupRepository,
            DiscordSyncService discordSyncService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.discordSyncService = discordSyncService;
    }

    @Transactional
    public List<MessageResponseDto> loadMessages(String currentUserId, String recipientId, int page, int pageSize) {
        String currentUserName = "";
        Optional<AppUser> currentUserOpt = userRepository.findById(currentUserId);
        if (currentUserOpt.isPresent()) {
            currentUserName = currentUserOpt.get().getUserName();
        } else {
            Optional<AppUser> byName = userRepository.findByUserName(currentUserId);
            if (byName.isPresent()) {
                currentUserName = byName.get().getUserName();
                currentUserId = byName.get().getId();
            }
        }

        String recipientUserName = "";
        Optional<AppUser> recipientOpt = userRepository.findById(recipientId);
        if (recipientOpt.isPresent()) {
            recipientUserName = recipientOpt.get().getUserName();
        } else {
            Optional<AppUser> byName = userRepository.findByUserName(recipientId);
            if (byName.isPresent()) {
                recipientUserName = byName.get().getUserName();
                recipientId = byName.get().getId();
            }
        }

        int pageIndex = Math.max(0, page - 1);
        List<Message> messages = messageRepository.findConversation(
                currentUserId, currentUserName,
                recipientId, recipientUserName,
                PageRequest.of(pageIndex, pageSize)
        );

        if (messages.isEmpty() && page <= 1 && messageRepository.count() == 0) {
            discordSyncService.syncFromDiscordOnStartup();
            messages = messageRepository.findConversation(
                    currentUserId, currentUserName,
                    recipientId, recipientUserName,
                    PageRequest.of(pageIndex, pageSize)
            );
        }

        messageRepository.markAsRead(currentUserId, currentUserName, recipientId, recipientUserName);

        List<MessageResponseDto> dtos = new ArrayList<>();
        for (Message msg : messages) {
            MessageResponseDto dto = new MessageResponseDto();
            dto.setId(msg.getId());
            dto.setSenderId(msg.getSenderId());
            dto.setReceiverId(msg.getReceiverId());
            dto.setContent(msg.getContent());
            dto.setCreatedDate(msg.getCreatedDate());
            dto.setRead(true);
            dtos.add(dto);
        }

        return dtos;
    }

    @Transactional
    public Message saveMessage(String senderId, String receiverId, String content) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setCreatedDate(Instant.now());
        message.setRead(false);
        Message saved = messageRepository.save(message);
        discordSyncService.backupMessageToDiscord(saved);
        return saved;
    }

    public int getUnreadCount(String currentUserId, String otherUserId) {
        String currentUserName = userRepository.findById(currentUserId).map(AppUser::getUserName).orElse(currentUserId);
        String otherUserName = userRepository.findById(otherUserId).map(AppUser::getUserName).orElse(otherUserId);

        return messageRepository.countUnreadMessages(currentUserId, currentUserName, otherUserId, otherUserName);
    }

    @Transactional
    public ChatGroup createGroup(String creatorId, String name, List<String> memberIds, String groupImage) {
        ChatGroup group = new ChatGroup(name, creatorId, memberIds);
        if (groupImage != null && !groupImage.isEmpty()) {
            group.setGroupImage(groupImage);
        }
        ChatGroup saved = chatGroupRepository.save(group);
        discordSyncService.backupGroupToDiscord(saved);
        return saved;
    }

    public List<ChatGroup> getUserGroups(String userId) {
        return chatGroupRepository.findByMemberId(userId);
    }

    public Optional<ChatGroup> getGroup(String groupId) {
        return chatGroupRepository.findById(groupId);
    }

    @Transactional
    public void addMemberToGroup(String groupId, String userId) {
        chatGroupRepository.findById(groupId).ifPresent(group -> {
            if (!group.getMemberIds().contains(userId)) {
                group.getMemberIds().add(userId);
                ChatGroup saved = chatGroupRepository.save(group);
                discordSyncService.backupGroupToDiscord(saved);
            }
        });
    }

    @Transactional
    public boolean leaveGroup(String groupId, String userId) {
        Optional<ChatGroup> opt = chatGroupRepository.findById(groupId);
        if (opt.isEmpty()) return false;
        ChatGroup group = opt.get();
        if (!group.getMemberIds().contains(userId)) return false;

        group.getMemberIds().remove(userId);
        if (group.getMemberIds().isEmpty()) {
            chatGroupRepository.delete(group);
            return true;
        }

        // If creator left, transfer creator role to the next member
        if (userId.equals(group.getCreatorId())) {
            group.setCreatorId(group.getMemberIds().get(0));
        }

        ChatGroup saved = chatGroupRepository.save(group);
        discordSyncService.backupGroupToDiscord(saved);
        return true;
    }

    @Transactional
    public boolean kickMemberFromGroup(String groupId, String requesterId, String memberIdToKick) {
        Optional<ChatGroup> opt = chatGroupRepository.findById(groupId);
        if (opt.isEmpty()) return false;
        ChatGroup group = opt.get();

        // Only group creator can kick members
        if (!requesterId.equals(group.getCreatorId())) {
            throw new IllegalArgumentException("Only the group creator can remove members");
        }

        if (requesterId.equals(memberIdToKick)) {
            throw new IllegalArgumentException("Creator cannot kick themselves. Use leave group instead.");
        }

        if (group.getMemberIds().remove(memberIdToKick)) {
            ChatGroup saved = chatGroupRepository.save(group);
            discordSyncService.backupGroupToDiscord(saved);
            return true;
        }
        return false;
    }

    public List<com.chatapp.dto.GroupMemberDto> getGroupMembers(String groupId) {
        Optional<ChatGroup> opt = chatGroupRepository.findById(groupId);
        if (opt.isEmpty()) return List.of();
        ChatGroup group = opt.get();

        List<com.chatapp.dto.GroupMemberDto> members = new ArrayList<>();
        for (String memberId : group.getMemberIds()) {
            Optional<AppUser> userOpt = userRepository.findById(memberId);
            if (userOpt.isPresent()) {
                AppUser u = userOpt.get();
                boolean isCreator = memberId.equals(group.getCreatorId());
                members.add(new com.chatapp.dto.GroupMemberDto(u.getId(), u.getFullName(), u.getUserName(), u.getProfileImage(), isCreator));
            } else {
                boolean isCreator = memberId.equals(group.getCreatorId());
                members.add(new com.chatapp.dto.GroupMemberDto(memberId, "User", memberId, null, isCreator));
            }
        }
        return members;
    }

    @Transactional
    public Message saveGroupMessage(String senderId, String groupId, String content) {
        String senderName = userRepository.findById(senderId)
                .map(AppUser::getFullName)
                .orElse(userRepository.findByUserName(senderId).map(AppUser::getFullName).orElse(senderId));

        Message message = new Message();
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setGroupId(groupId);
        message.setContent(content);
        message.setCreatedDate(Instant.now());
        message.setRead(true);
        Message saved = messageRepository.save(message);
        discordSyncService.backupMessageToDiscord(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MessageResponseDto> loadGroupMessages(String groupId, int page, int pageSize) {
        int pageIndex = Math.max(0, page - 1);
        List<Message> messages = messageRepository.findGroupMessages(groupId, PageRequest.of(pageIndex, pageSize));

        if (messages.isEmpty() && page <= 1 && messageRepository.count() == 0) {
            discordSyncService.syncFromDiscordOnStartup();
            messages = messageRepository.findGroupMessages(groupId, PageRequest.of(pageIndex, pageSize));
        }

        List<MessageResponseDto> dtos = new ArrayList<>();
        for (Message msg : messages) {
            MessageResponseDto dto = new MessageResponseDto();
            dto.setId(msg.getId());
            dto.setSenderId(msg.getSenderId());
            dto.setReceiverId(msg.getGroupId());
            dto.setGroupId(msg.getGroupId());
            dto.setSenderName(msg.getSenderName());
            dto.setContent(msg.getContent());
            dto.setCreatedDate(msg.getCreatedDate());
            dto.setRead(true);
            dtos.add(dto);
        }
        return dtos;
    }
}
