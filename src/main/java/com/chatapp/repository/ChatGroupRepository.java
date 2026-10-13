package com.chatapp.repository;

import com.chatapp.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatGroupRepository extends JpaRepository<ChatGroup, String> {

    @Query("SELECT g FROM ChatGroup g JOIN g.memberIds m WHERE m = :userId ORDER BY g.createdDate DESC")
    List<ChatGroup> findByMemberId(@Param("userId") String userId);
}
