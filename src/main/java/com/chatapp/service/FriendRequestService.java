package com.chatapp.service;

import com.chatapp.model.AppUser;
import com.chatapp.model.FriendsRequest;
import com.chatapp.repository.AppUserRepository;
import com.chatapp.repository.FriendsRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FriendRequestService {

    private final FriendsRequestRepository friendsRequestRepository;
    private final AppUserRepository userRepository;

    public FriendRequestService(FriendsRequestRepository friendsRequestRepository, AppUserRepository userRepository) {
        this.friendsRequestRepository = friendsRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void sendFriendRequest(String senderId, String receiverId) {
        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            return;
        }

        Optional<FriendsRequest> existing = friendsRequestRepository.findExistingRequest(senderId, receiverId);
        if (existing.isEmpty()) {
            FriendsRequest request = new FriendsRequest(senderId, receiverId);
            friendsRequestRepository.save(request);
        }
    }

    @Transactional
    public void acceptFriendRequest(String senderId, String receiverId) {
        Optional<FriendsRequest> existing = friendsRequestRepository.findExistingRequest(senderId, receiverId);
        if (existing.isPresent()) {
            Optional<AppUser> senderOpt = userRepository.findById(senderId);
            Optional<AppUser> receiverOpt = userRepository.findById(receiverId);

            if (senderOpt.isPresent() && receiverOpt.isPresent()) {
                AppUser sender = senderOpt.get();
                AppUser receiver = receiverOpt.get();

                if (!sender.getFriendsId().contains(receiverId)) {
                    sender.getFriendsId().add(receiverId);
                }
                if (!receiver.getFriendsId().contains(senderId)) {
                    receiver.getFriendsId().add(senderId);
                }

                userRepository.save(sender);
                userRepository.save(receiver);

                friendsRequestRepository.delete(existing.get());
            }
        }
    }

    public List<FriendsRequest> getRequestsForMe(String receiverId) {
        return friendsRequestRepository.findByReceiverId(receiverId);
    }

    public List<FriendsRequest> getRequestsSendByMe(String senderId) {
        return friendsRequestRepository.findBySenderId(senderId);
    }
}
