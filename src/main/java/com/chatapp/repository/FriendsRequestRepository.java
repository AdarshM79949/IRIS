package com.chatapp.repository;

import com.chatapp.model.FriendsRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendsRequestRepository extends JpaRepository<FriendsRequest, Integer> {
    
    @Query("SELECT r FROM FriendsRequest r WHERE (r.senderId = :u1 AND r.receiverId = :u2) OR (r.senderId = :u2 AND r.receiverId = :u1)")
    List<FriendsRequest> findRequestsBetween(@Param("u1") String u1, @Param("u2") String u2);

    List<FriendsRequest> findByReceiverId(String receiverId);

    List<FriendsRequest> findBySenderId(String senderId);
}
