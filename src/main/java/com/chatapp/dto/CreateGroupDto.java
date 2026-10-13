package com.chatapp.dto;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupDto {

    private String name;
    private List<String> memberIds = new ArrayList<>();
    private String groupImage;

    public CreateGroupDto() {
    }

    public CreateGroupDto(String name, List<String> memberIds) {
        this.name = name;
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberIds() {
        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
    }

    public String getGroupImage() {
        return groupImage;
    }

    public void setGroupImage(String groupImage) {
        this.groupImage = groupImage;
    }
}
