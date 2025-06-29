package com.yumsg.core.data;

import android.util.Base64;

public class PeerCryptoInfo {
    private String peerId;
    private CryptoAlgorithms peerAlgorithms;
    private byte[] peerSignaturePublicKey;
    private String peerSignatureAlgorithm;
    private long lastUpdated;
    private boolean verified;

    public PeerCryptoInfo() {
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }

    public PeerCryptoInfo(String peerId, CryptoAlgorithms peerAlgorithms,
                         byte[] peerSignaturePublicKey, String peerSignatureAlgorithm) {
        this.peerId = peerId;
        this.peerAlgorithms = peerAlgorithms;
        this.peerSignaturePublicKey = peerSignaturePublicKey;
        this.peerSignatureAlgorithm = peerSignatureAlgorithm;
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public CryptoAlgorithms getPeerAlgorithms() { return peerAlgorithms; }
    public void setPeerAlgorithms(CryptoAlgorithms peerAlgorithms) {
        this.peerAlgorithms = peerAlgorithms;
        this.lastUpdated = System.currentTimeMillis();
    }

    public byte[] getPeerSignaturePublicKey() { return peerSignaturePublicKey; }
    public void setPeerSignaturePublicKey(byte[] peerSignaturePublicKey) {
        this.peerSignaturePublicKey = peerSignaturePublicKey;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getPeerSignatureAlgorithm() { return peerSignatureAlgorithm; }
    public void setPeerSignatureAlgorithm(String peerSignatureAlgorithm) {
        this.peerSignatureAlgorithm = peerSignatureAlgorithm;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public boolean isComplete() {
        return peerId != null && !peerId.trim().isEmpty() &&
               peerAlgorithms != null && peerAlgorithms.isValid() &&
               peerSignaturePublicKey != null && peerSignaturePublicKey.length > 0 &&
               peerSignatureAlgorithm != null && !peerSignatureAlgorithm.trim().isEmpty();
    }

    public String getPeerSignaturePublicKeyBase64() {
        return peerSignaturePublicKey != null ?
               Base64.encodeToString(peerSignaturePublicKey, Base64.NO_WRAP) : null;
    }

    public void setPeerSignaturePublicKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.peerSignaturePublicKey = Base64.decode(base64Key, Base64.NO_WRAP);
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public boolean isCompatibleWith(CryptoAlgorithms myAlgorithms) {
        if (peerAlgorithms == null || myAlgorithms == null) {
            return false;
        }
        return peerSignatureAlgorithm != null &&
               peerSignatureAlgorithm.equals(myAlgorithms.getSignatureAlgorithm());
    }

    @Override
    public String toString() {
        return "PeerCryptoInfo{" +
                "peerId='" + peerId + '\'' +
                ", peerAlgorithms=" + peerAlgorithms +
                ", hasSignatureKey=" + (peerSignaturePublicKey != null && peerSignaturePublicKey.length > 0) +
                ", peerSignatureAlgorithm='" + peerSignatureAlgorithm + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", verified=" + verified +
                '}';
    }
}
