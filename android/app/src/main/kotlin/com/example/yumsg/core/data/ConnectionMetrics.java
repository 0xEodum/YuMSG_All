package com.yumsg.core.data;

public class ConnectionMetrics {
    private long latency;
    private long bandwidth;
    private int packetLoss;
    private String connectionType;
    private long connectedTime;

    public ConnectionMetrics() {
        this.connectedTime = System.currentTimeMillis();
    }

    public long getLatency() { return latency; }
    public void setLatency(long latency) { this.latency = latency; }
    public long getBandwidth() { return bandwidth; }
    public void setBandwidth(long bandwidth) { this.bandwidth = bandwidth; }
    public int getPacketLoss() { return packetLoss; }
    public void setPacketLoss(int packetLoss) { this.packetLoss = packetLoss; }
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
    public long getConnectedTime() { return connectedTime; }
}
