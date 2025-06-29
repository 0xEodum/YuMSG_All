package com.yumsg.core.data;

public class Organization {
    private String name;
    private byte[] userSignaturePublicKey;
    private byte[] userSignaturePrivateKey;
    private CryptoAlgorithms cryptoAlgorithms;

    public Organization(String name) { this.name = name; }

    public String getName() { return name; }
    public byte[] getUserSignaturePublicKey() { return userSignaturePublicKey; }
    public void setUserSignaturePublicKey(byte[] key) { this.userSignaturePublicKey = key; }
    public byte[] getUserSignaturePrivateKey() { return userSignaturePrivateKey; }
    public void setUserSignaturePrivateKey(byte[] key) { this.userSignaturePrivateKey = key; }
    public CryptoAlgorithms getCryptoAlgorithms() { return cryptoAlgorithms; }
    public void setCryptoAlgorithms(CryptoAlgorithms algorithms) { this.cryptoAlgorithms = algorithms; }
}
