package com.chatapp.config;

import java.security.Principal;

public class UserPrincipal implements Principal {

    private final String id;
    private final String name;

    public UserPrincipal(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}
