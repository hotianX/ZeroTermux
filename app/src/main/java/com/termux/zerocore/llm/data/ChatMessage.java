package com.termux.zerocore.llm.data;

public class ChatMessage {
    private String messageText;
    private String reasoningText;
    private String toolCallsJson;
    private boolean isUser; // True if the message is from the user, false otherwise.
    private long timestamp;
    private int avatarResId;

    public ChatMessage(String messageText, boolean isUser, long timestamp, int avatarResId) {
        this(messageText, null, null, isUser, timestamp, avatarResId);
    }

    public ChatMessage(String messageText, String reasoningText, boolean isUser, long timestamp, int avatarResId) {
        this(messageText, reasoningText, null, isUser, timestamp, avatarResId);
    }

    public ChatMessage(String messageText, String reasoningText, String toolCallsJson,
                       boolean isUser, long timestamp, int avatarResId) {
        this.messageText = messageText;
        this.reasoningText = reasoningText;
        this.toolCallsJson = toolCallsJson;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.avatarResId = avatarResId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void appendMessageText(String additionalText) {
        this.messageText += additionalText;
    }

    public String getReasoningText() {
        return reasoningText;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public void appendReasoningText(String additionalText) {
        if (this.reasoningText == null) {
            this.reasoningText = "";
        }
        this.reasoningText += additionalText;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getAvatarResId() {
        return avatarResId;
    }
}
