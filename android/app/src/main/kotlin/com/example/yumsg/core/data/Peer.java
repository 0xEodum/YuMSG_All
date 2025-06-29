package com.yumsg.core.data;

import com.yumsg.core.enums.UserStatus;

public class Peer {
    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private UserStatus status;
    private long lastSeen;

    public Peer(String id, String name, String ipAddress, int port) {
        this.id = id;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public long getLastSeen() { return lastSeen; }
    public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }
}
