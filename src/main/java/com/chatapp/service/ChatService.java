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
public class ChatService {

    private final MessageRepository messageRepository;
    private final AppUserRepository userRepository;
    private final ChatGroupRepository chatGroupRepository;

    public ChatService(
            MessageRepository messageRepository,
            AppUserRepository userRepository,
            ChatGroupRepository chatGroupRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatGroupRepository = chatGroupRepository;
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
        return messageRepository.save(message);
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
        return chatGroupRepository.save(group);
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
                chatGroupRepository.save(group);
            }
        });
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
        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<MessageResponseDto> loadGroupMessages(String groupId, int page, int pageSize) {
        int pageIndex = Math.max(0, page - 1);
        List<Message> messages = messageRepository.findGroupMessages(groupId, PageRequest.of(pageIndex, pageSize));

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
