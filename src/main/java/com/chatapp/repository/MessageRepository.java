package com.chatapp.repository;

import com.chatapp.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    @Query("SELECT m FROM Message m WHERE " +
           "((m.senderId = :u1 OR m.senderId = :u1Name) AND (m.receiverId = :u2 OR m.receiverId = :u2Name)) " +
           "OR " +
           "((m.senderId = :u2 OR m.senderId = :u2Name) AND (m.receiverId = :u1 OR m.receiverId = :u1Name)) " +
           "ORDER BY m.createdDate ASC")
    List<Message> findConversation(
            @Param("u1") String u1,
            @Param("u1Name") String u1Name,
            @Param("u2") String u2,
            @Param("u2Name") String u2Name,
            Pageable pageable
    );

    @Query("SELECT COUNT(m) FROM Message m WHERE " +
           "(m.receiverId = :receiverId OR m.receiverId = :receiverName) AND " +
           "(m.senderId = :senderId OR m.senderId = :senderName) AND " +
           "m.isRead = false")
    int countUnreadMessages(
            @Param("receiverId") String receiverId,
            @Param("receiverName") String receiverName,
            @Param("senderId") String senderId,
            @Param("senderName") String senderName
    );

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE " +
           "(m.receiverId = :receiverId OR m.receiverId = :receiverName) AND " +
           "(m.senderId = :senderId OR m.senderId = :senderName) AND " +
           "m.isRead = false")
    void markAsRead(
            @Param("receiverId") String receiverId,
            @Param("receiverName") String receiverName,
            @Param("senderId") String senderId,
            @Param("senderName") String senderName
    );

    @Query("SELECT m FROM Message m WHERE m.groupId = :groupId ORDER BY m.createdDate ASC")
    List<Message> findGroupMessages(@Param("groupId") String groupId, Pageable pageable);

    @Query("SELECT COUNT(m) > 0 FROM Message m WHERE m.senderId = :senderId AND (m.receiverId = :receiverId OR (m.receiverId IS NULL AND :receiverId IS NULL)) AND (m.groupId = :groupId OR (m.groupId IS NULL AND :groupId IS NULL)) AND m.createdDate = :createdDate AND m.content = :content")
    boolean existsSimilar(
            @Param("senderId") String senderId,
            @Param("receiverId") String receiverId,
            @Param("groupId") String groupId,
            @Param("createdDate") java.time.Instant createdDate,
            @Param("content") String content
    );
}
