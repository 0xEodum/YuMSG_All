package com.example.yumsg.core.data;

import com.example.yumsg.core.enums.UserStatus;

public class User {
    private String id;
    private String username;
    private UserStatus status;

    public User() {}
    public User(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
