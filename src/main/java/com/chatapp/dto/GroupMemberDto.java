package com.chatapp.dto;

public class GroupMemberDto {
    private String id;
    private String fullName;
    private String userName;
    private String profileImage;
    private boolean isCreator;

    public GroupMemberDto() {}

    public GroupMemberDto(String id, String fullName, String userName, String profileImage, boolean isCreator) {
        this.id = id;
        this.fullName = fullName;
        this.userName = userName;
        this.profileImage = profileImage;
        this.isCreator = isCreator;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public boolean isCreator() { return isCreator; }
    public void setCreator(boolean creator) { isCreator = creator; }
}
