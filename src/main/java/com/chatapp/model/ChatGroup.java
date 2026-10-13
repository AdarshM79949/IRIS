package com.chatapp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ChatGroups")
public class ChatGroup {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String creatorId;

    private String groupImage;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id")
    private List<String> memberIds = new ArrayList<>();

    public ChatGroup() {
        this.id = UUID.randomUUID().toString();
        this.createdDate = Instant.now();
    }

    public ChatGroup(String name, String creatorId, List<String> memberIds) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.creatorId = creatorId;
        this.createdDate = Instant.now();
        this.memberIds = memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>();
        if (creatorId != null && !this.memberIds.contains(creatorId)) {
            this.memberIds.add(creatorId);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getGroupImage() {
        return groupImage;
    }

    public void setGroupImage(String groupImage) {
        this.groupImage = groupImage;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public List<String> getMemberIds() {
        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>();
    }
}
