package com.yumsg.core.data;

import com.yumsg.core.enums.AlgorithmType;

public class AlgorithmInfo {
    private String name;
    private AlgorithmType type;
    private int keySize;
    private String description;
    private boolean recommended;
    private String securityLevel;

    public AlgorithmInfo(String name, AlgorithmType type, int keySize, String description, boolean recommended, String securityLevel) {
        this.name = name;
        this.type = type;
        this.keySize = keySize;
        this.description = description;
        this.recommended = recommended;
        this.securityLevel = securityLevel;
    }

    public String getName() { return name; }
    public AlgorithmType getType() { return type; }
    public int getKeySize() { return keySize; }
    public String getDescription() { return description; }
    public boolean isRecommended() { return recommended; }
    public String getSecurityLevel() { return securityLevel; }
}
