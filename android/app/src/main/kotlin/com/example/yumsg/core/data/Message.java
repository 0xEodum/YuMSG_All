package com.yumsg.core.data;

import com.yumsg.core.enums.MessageStatus;

public class Message {
    private long id;
    private String chatId;
    private String content;
    private MessageStatus status;
    private long timestamp;

    public Message() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
