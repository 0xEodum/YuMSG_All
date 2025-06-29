package com.yumsg.core.data;

public class CryptoAlgorithms {
    private String kemAlgorithm;
    private String symmetricAlgorithm;
    private String signatureAlgorithm;

    public CryptoAlgorithms() {}
    public CryptoAlgorithms(String kemAlgorithm, String symmetricAlgorithm, String signatureAlgorithm) {
        this.kemAlgorithm = kemAlgorithm;
        this.symmetricAlgorithm = symmetricAlgorithm;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getKemAlgorithm() { return kemAlgorithm; }
    public void setKemAlgorithm(String algorithm) { this.kemAlgorithm = algorithm; }
    public String getSymmetricAlgorithm() { return symmetricAlgorithm; }
    public void setSymmetricAlgorithm(String algorithm) { this.symmetricAlgorithm = algorithm; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String algorithm) { this.signatureAlgorithm = algorithm; }
    public boolean isValid() { /* validation logic */ return true; }
    public boolean equals(CryptoAlgorithms other) { /* comparison logic */ return false; }
}
