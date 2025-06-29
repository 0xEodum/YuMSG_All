package com.example.yumsg.core.data;

import android.util.Base64;
import java.util.Arrays;

public class OrganizationKeys {
    private String organizationName;
    private String signatureAlgorithm;
    private byte[] publicSignatureKey;
    private byte[] privateSignatureKey;
    private long createdAt;
    private boolean isActive;

    public OrganizationKeys() {
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }

    public OrganizationKeys(String organizationName, String signatureAlgorithm,
                           byte[] publicSignatureKey, byte[] privateSignatureKey) {
        this.organizationName = organizationName;
        this.signatureAlgorithm = signatureAlgorithm;
        this.publicSignatureKey = publicSignatureKey;
        this.privateSignatureKey = privateSignatureKey;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }

    public byte[] getPublicSignatureKey() { return publicSignatureKey; }
    public void setPublicSignatureKey(byte[] publicSignatureKey) { this.publicSignatureKey = publicSignatureKey; }

    public byte[] getPrivateSignatureKey() { return privateSignatureKey; }
    public void setPrivateSignatureKey(byte[] privateSignatureKey) { this.privateSignatureKey = privateSignatureKey; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isComplete() {
        return organizationName != null && !organizationName.trim().isEmpty() &&
               signatureAlgorithm != null && !signatureAlgorithm.trim().isEmpty() &&
               publicSignatureKey != null && publicSignatureKey.length > 0 &&
               privateSignatureKey != null && privateSignatureKey.length > 0;
    }

    public String getPublicKeyBase64() {
        return publicSignatureKey != null ? Base64.encodeToString(publicSignatureKey, Base64.NO_WRAP) : null;
    }

    public void setPublicKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.publicSignatureKey = Base64.decode(base64Key, Base64.NO_WRAP);
        }
    }

    public String getPrivateKeyBase64() {
        return privateSignatureKey != null ? Base64.encodeToString(privateSignatureKey, Base64.NO_WRAP) : null;
    }

    public void setPrivateKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.privateSignatureKey = Base64.decode(base64Key, Base64.NO_WRAP);
        }
    }

    public String getId() {
        return organizationName + ":" + signatureAlgorithm;
    }

    public void secureWipe() {
        if (privateSignatureKey != null) {
            Arrays.fill(privateSignatureKey, (byte) 0);
        }
        if (publicSignatureKey != null) {
            Arrays.fill(publicSignatureKey, (byte) 0);
        }
    }

    @Override
    public String toString() {
        return "OrganizationKeys{" +
                "organizationName='" + organizationName + '\'' +
                ", signatureAlgorithm='" + signatureAlgorithm + '\'' +
                ", hasPublicKey=" + (publicSignatureKey != null && publicSignatureKey.length > 0) +
                ", hasPrivateKey=" + (privateSignatureKey != null && privateSignatureKey.length > 0) +
                ", createdAt=" + createdAt +
                ", isActive=" + isActive +
                '}';
    }
}
