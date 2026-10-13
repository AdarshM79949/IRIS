package com.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class LoginDto {

    @JsonAlias({"Email", "email"})
    private String email;

    @JsonAlias({"Password", "password"})
    private String password;

    public LoginDto() {
    }

    public LoginDto(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
