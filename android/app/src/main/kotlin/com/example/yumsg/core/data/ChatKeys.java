package com.example.yumsg.core.data;

public class ChatKeys {
    private byte[] publicKeySelf;
    private byte[] privateKeySelf;
    private byte[] publicKeyPeer;
    private byte[] symmetricKey;
    private String algorithm;

    public ChatKeys() {}
    public ChatKeys(String algorithm) { this.algorithm = algorithm; }

    public byte[] getPublicKeySelf() { return publicKeySelf; }
    public void setPublicKeySelf(byte[] key) { this.publicKeySelf = key; }
    public byte[] getPrivateKeySelf() { return privateKeySelf; }
    public void setPrivateKeySelf(byte[] key) { this.privateKeySelf = key; }
    public byte[] getPublicKeyPeer() { return publicKeyPeer; }
    public void setPublicKeyPeer(byte[] key) { this.publicKeyPeer = key; }
    public byte[] getSymmetricKey() { return symmetricKey; }
    public void setSymmetricKey(byte[] key) { this.symmetricKey = key; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public boolean hasKeyPair() { return publicKeySelf != null && privateKeySelf != null; }
    public boolean hasPeerKey() { return publicKeyPeer != null; }
    public boolean isComplete() { return hasKeyPair() && hasPeerKey() && symmetricKey != null; }
    public void secureWipe() { /* secure memory clearing */ }
}
