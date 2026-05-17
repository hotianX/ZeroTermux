package com.termux.zerocore.ai.llm.data;

public class ChatMessage {
    private String messageText;
    private boolean isUser;
    private long timestamp;
    private int avatarResId;
    private String reasoningText;
    private String toolCallsJson;

    public ChatMessage(String messageText, boolean isUser, long timestamp, int avatarResId) {
        this(messageText, isUser, timestamp, avatarResId, "", "");
    }

    public ChatMessage(String messageText, boolean isUser, long timestamp, int avatarResId,
                       String reasoningText, String toolCallsJson) {
        this.messageText = messageText != null ? messageText : "";
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.avatarResId = avatarResId;
        this.reasoningText = reasoningText != null ? reasoningText : "";
        this.toolCallsJson = toolCallsJson != null ? toolCallsJson : "";
    }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText != null ? messageText : ""; }

    public void appendMessageText(String additionalText) {
        this.messageText += additionalText != null ? additionalText : "";
    }

    public boolean isUser() { return isUser; }
    public long getTimestamp() { return timestamp; }
    public int getAvatarResId() { return avatarResId; }

    public String getReasoningText() { return reasoningText; }
    public void setReasoningText(String reasoningText) { this.reasoningText = reasoningText != null ? reasoningText : ""; }
    public void appendReasoningText(String additionalText) { this.reasoningText += additionalText != null ? additionalText : ""; }

    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson != null ? toolCallsJson : ""; }
    public void appendToolCallsJson(String additionalText) { this.toolCallsJson += additionalText != null ? additionalText : ""; }
}
