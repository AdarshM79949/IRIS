package com.chatapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "AspNetUsers")
public class AppUser {

    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String userName;

    private String fullName;

    private String profileImage;

    @JsonIgnore
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_friends", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "friend_id")
    @JsonProperty("friendsId")
    private List<String> friendsId = new ArrayList<>();

    public AppUser() {
        this.id = UUID.randomUUID().toString();
    }

    public AppUser(String email, String userName, String fullName, String profileImage, String password) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.userName = userName;
        this.fullName = fullName;
        this.profileImage = profileImage;
        this.password = password;
        this.friendsId = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getFriendsId() {
        if (friendsId == null) {
            friendsId = new ArrayList<>();
        }
        return friendsId;
    }

    public void setFriendsId(List<String> friendsId) {
        this.friendsId = friendsId != null ? friendsId : new ArrayList<>();
    }
}
