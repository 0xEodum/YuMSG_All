package com.yumsg.core.data;

import java.util.HashMap;
import java.util.Map;

public class OrganizationInfo {
    private String name;
    private String id;
    private CryptoAlgorithms supportedAlgorithms;
    private String serverVersion;
    private Map<String, Object> policies;

    public OrganizationInfo(String name, String id) {
        this.name = name;
        this.id = id;
        this.policies = new HashMap<>();
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public CryptoAlgorithms getSupportedAlgorithms() { return supportedAlgorithms; }
    public void setSupportedAlgorithms(CryptoAlgorithms algorithms) { this.supportedAlgorithms = algorithms; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String version) { this.serverVersion = version; }
    public Map<String, Object> getPolicies() { return policies; }
}
