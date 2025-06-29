package com.example.yumsg.core.data;

public class UserCredentials {
    private String username;
    private String password;
    private String email;

    public UserCredentials(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
}
