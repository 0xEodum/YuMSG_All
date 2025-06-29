package com.example.yumsg.core.data;

public class ServerConfig {
    private String host;
    private int port;
    private String organizationName;

    public ServerConfig(String host, int port, String organizationName) {
        this.host = host;
        this.port = port;
        this.organizationName = organizationName;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getOrganizationName() { return organizationName; }
}
