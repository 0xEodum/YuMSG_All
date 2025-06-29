package com.yumsg.core.data;

import java.util.HashMap;
import java.util.Map;

public class NotificationData {
    private String id;
    private String title;
    private String message;
    private String type;
    private Map<String, Object> extras;

    public NotificationData(String id, String title, String message, String type) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.extras = new HashMap<>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public Map<String, Object> getExtras() { return extras; }
}
