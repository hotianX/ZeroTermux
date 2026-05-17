package com.termux.zerocore.ai.model;

/** Structured provider stream event used by all AI protocols. */
public class ProviderStreamEvent {
    public static final String TYPE_CONTENT_DELTA = "content_delta";
    public static final String TYPE_REASONING_DELTA = "reasoning_delta";
    public static final String TYPE_TOOL_CALL_DELTA = "tool_call_delta";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_RAW_DEBUG_REDACTED = "raw_debug_redacted";

    private final String type;
    private final String textDelta;
    private final String reasoningDelta;
    private final String toolCallId;
    private final String toolName;
    private final String toolArgumentsDelta;
    private final String finishReason;
    private final String providerEventName;
    private final String redactedRawPayload;
    private final String errorMessage;

    public static final String TOOL_DISPLAY_ONLY_NOTICE = "\u4ec5\u5c55\u793a\uff0c\u4e0d\u4f1a\u81ea\u52a8\u6267\u884c";

    private ProviderStreamEvent(Builder builder) {
        this.type = builder.type;
        this.textDelta = builder.textDelta;
        this.reasoningDelta = builder.reasoningDelta;
        this.toolCallId = builder.toolCallId;
        this.toolName = builder.toolName;
        this.toolArgumentsDelta = builder.toolArgumentsDelta;
        this.finishReason = builder.finishReason;
        this.providerEventName = builder.providerEventName;
        this.redactedRawPayload = builder.redactedRawPayload;
        this.errorMessage = builder.errorMessage;
    }

    public static ProviderStreamEvent content(String text) {
        return new Builder(TYPE_CONTENT_DELTA).textDelta(text).build();
    }

    public static ProviderStreamEvent reasoning(String text) {
        return new Builder(TYPE_REASONING_DELTA).reasoningDelta(text).build();
    }

    public static ProviderStreamEvent toolCall(String id, String name, String argsDelta) {
        return new Builder(TYPE_TOOL_CALL_DELTA).toolCallId(id).toolName(name).toolArgumentsDelta(argsDelta).build();
    }

    public static ProviderStreamEvent done(String finishReason) {
        return new Builder(TYPE_DONE).finishReason(finishReason).build();
    }

    public static ProviderStreamEvent error(String message) {
        return new Builder(TYPE_ERROR).errorMessage(message).build();
    }

    public String getType() { return type; }
    public String getTextDelta() { return textDelta; }
    public String getReasoningDelta() { return reasoningDelta; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public String getToolArgumentsDelta() { return toolArgumentsDelta; }
    public String getFinishReason() { return finishReason; }
    public String getProviderEventName() { return providerEventName; }
    public String getRedactedRawPayload() { return redactedRawPayload; }
    public String getErrorMessage() { return errorMessage; }

    public static class Builder {
        private final String type;
        private String textDelta;
        private String reasoningDelta;
        private String toolCallId;
        private String toolName;
        private String toolArgumentsDelta;
        private String finishReason;
        private String providerEventName;
        private String redactedRawPayload;
        private String errorMessage;

        public Builder(String type) { this.type = type; }
        public Builder textDelta(String value) { this.textDelta = value; return this; }
        public Builder reasoningDelta(String value) { this.reasoningDelta = value; return this; }
        public Builder toolCallId(String value) { this.toolCallId = value; return this; }
        public Builder toolName(String value) { this.toolName = value; return this; }
        public Builder toolArgumentsDelta(String value) { this.toolArgumentsDelta = value; return this; }
        public Builder finishReason(String value) { this.finishReason = value; return this; }
        public Builder providerEventName(String value) { this.providerEventName = value; return this; }
        public Builder redactedRawPayload(String value) { this.redactedRawPayload = value; return this; }
        public Builder errorMessage(String value) { this.errorMessage = value; return this; }
        public ProviderStreamEvent build() { return new ProviderStreamEvent(this); }
    }
}
