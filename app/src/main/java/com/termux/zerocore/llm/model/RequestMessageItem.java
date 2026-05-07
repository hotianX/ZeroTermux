package com.termux.zerocore.llm.model;

public class RequestMessageItem {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_TOOL = "tool";

    public String role;
    public String content;
    public String reasoningContent;
    public String toolCallId;
    public String toolCallsJson;

    public RequestMessageItem() {
    }

    public RequestMessageItem(String role, String content) {
        this(role, content, null, null, null);
    }

    public RequestMessageItem(String role, String content, String reasoningContent,
                              String toolCallId, String toolCallsJson) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
        this.toolCallsJson = toolCallsJson;
    }
}
