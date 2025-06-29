package com.example.yumsg.core.data;

import java.util.Map;

public class UIEvent {
    private String type;
    private Map<String, Object> data;

    public UIEvent(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public String getType() { return type; }
    public Map<String, Object> getData() { return data; }
}
