package com.yumsg.core.data;

public class ChatResult {
    private boolean success;
    private Chat chat;
    private String message;

    public ChatResult(boolean success, Chat chat, String message) {
        this.success = success;
        this.chat = chat;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public Chat getChat() { return chat; }
    public String getMessage() { return message; }
}
