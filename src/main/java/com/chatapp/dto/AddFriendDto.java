package com.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class AddFriendDto {

    @JsonAlias({"senderId", "SenderId"})
    private String senderId;

    @JsonAlias({"receiverId", "ReceiverId"})
    private String receiverId;

    public AddFriendDto() {
    }

    public AddFriendDto(String senderId, String receiverId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
}
