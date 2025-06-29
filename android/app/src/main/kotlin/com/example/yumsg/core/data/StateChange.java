package com.example.yumsg.core.data;

public class StateChange {
    private String type;
    private Object oldValue;
    private Object newValue;
    private long timestamp;

    public StateChange(String type, Object oldValue, Object newValue) {
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() { return type; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }
    public long getTimestamp() { return timestamp; }
}
