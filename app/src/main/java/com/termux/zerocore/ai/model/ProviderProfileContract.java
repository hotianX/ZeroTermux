package com.termux.zerocore.ai.model;

/** Stable persisted string constants for AI provider profiles. */
public final class ProviderProfileContract {
    private ProviderProfileContract() {}

    public static final String PROTOCOL_OPENAI_CHAT = "openai_chat";
    public static final String PROTOCOL_CLAUDE_MESSAGES = "claude_messages";
    public static final String PROTOCOL_GEMINI_GENERATE_CONTENT = "gemini_generate_content";

    public static final String ENDPOINT_OPENAI_V1_CHAT = "openai_v1_chat";
    public static final String ENDPOINT_DEEPSEEK_NO_V1_CHAT = "deepseek_no_v1_chat";
    public static final String ENDPOINT_CLAUDE_V1_MESSAGES = "claude_v1_messages";
    public static final String ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT = "gemini_v1beta_generate_content";

    public static final String AUTH_BEARER_AUTHORIZATION = "bearer_authorization";
    public static final String AUTH_ANTHROPIC_X_API_KEY = "anthropic_x_api_key";
    public static final String AUTH_GOOGLE_X_GOOG_API_KEY = "google_x_goog_api_key";
    public static final String AUTH_QUERY_KEY_COMPAT_ONLY = "query_key_compat_only";

    public static final String LEGACY_FORMAT_OPENAI = "openai";
    public static final String LEGACY_FORMAT_CLAUDE = "claude";
    public static final String LEGACY_FORMAT_GEMINI = "gemini";

    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    public static final String OPENAI_BASE_URL = "https://api.openai.com";
    public static final String CLAUDE_BASE_URL = "https://api.anthropic.com";
    public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-v4-flash";
    public static final String DEEPSEEK_PRO_MODEL = "deepseek-v4-pro";
    public static final String DEEPSEEK_LEGACY_CHAT_MODEL = "deepseek-chat";
    public static final String DEEPSEEK_LEGACY_REASONER_MODEL = "deepseek-reasoner";

    public static String normalizeProtocol(String value) {
        if (value == null || value.trim().isEmpty()) return PROTOCOL_OPENAI_CHAT;
        String v = value.trim();
        switch (v) {
            case PROTOCOL_OPENAI_CHAT:
            case PROTOCOL_CLAUDE_MESSAGES:
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return v;
            case LEGACY_FORMAT_CLAUDE:
                return PROTOCOL_CLAUDE_MESSAGES;
            case LEGACY_FORMAT_GEMINI:
                return PROTOCOL_GEMINI_GENERATE_CONTENT;
            case LEGACY_FORMAT_OPENAI:
            default:
                return PROTOCOL_OPENAI_CHAT;
        }
    }

    public static boolean isKnownEndpointPolicy(String value) {
        if (value == null) return false;
        switch (value.trim()) {
            case ENDPOINT_OPENAI_V1_CHAT:
            case ENDPOINT_DEEPSEEK_NO_V1_CHAT:
            case ENDPOINT_CLAUDE_V1_MESSAGES:
            case ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT:
                return true;
            default:
                return false;
        }
    }

    public static boolean isKnownAuthMode(String value) {
        if (value == null) return false;
        switch (value.trim()) {
            case AUTH_BEARER_AUTHORIZATION:
            case AUTH_ANTHROPIC_X_API_KEY:
            case AUTH_GOOGLE_X_GOOG_API_KEY:
            case AUTH_QUERY_KEY_COMPAT_ONLY:
                return true;
            default:
                return false;
        }
    }

    public static String defaultEndpointPolicy(String protocol) {
        switch (normalizeProtocol(protocol)) {
            case PROTOCOL_CLAUDE_MESSAGES:
                return ENDPOINT_CLAUDE_V1_MESSAGES;
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT;
            case PROTOCOL_OPENAI_CHAT:
            default:
                return ENDPOINT_OPENAI_V1_CHAT;
        }
    }

    public static String defaultAuthMode(String protocol) {
        switch (normalizeProtocol(protocol)) {
            case PROTOCOL_CLAUDE_MESSAGES:
                return AUTH_ANTHROPIC_X_API_KEY;
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return AUTH_GOOGLE_X_GOOG_API_KEY;
            case PROTOCOL_OPENAI_CHAT:
            default:
                return AUTH_BEARER_AUTHORIZATION;
        }
    }

    public static String defaultModelName(String protocol) {
        switch (normalizeProtocol(protocol)) {
            case PROTOCOL_CLAUDE_MESSAGES:
                return "claude-sonnet-4-5";
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return "gemini-2.5-flash";
            case PROTOCOL_OPENAI_CHAT:
            default:
                return "gpt-4o";
        }
    }

    public static String defaultBaseUrl(String protocol) {
        switch (normalizeProtocol(protocol)) {
            case PROTOCOL_CLAUDE_MESSAGES:
                return CLAUDE_BASE_URL;
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return GEMINI_BASE_URL;
            case PROTOCOL_OPENAI_CHAT:
            default:
                return OPENAI_BASE_URL;
        }
    }

    public static String legacyFormatForProtocol(String protocol) {
        switch (normalizeProtocol(protocol)) {
            case PROTOCOL_CLAUDE_MESSAGES:
                return LEGACY_FORMAT_CLAUDE;
            case PROTOCOL_GEMINI_GENERATE_CONTENT:
                return LEGACY_FORMAT_GEMINI;
            case PROTOCOL_OPENAI_CHAT:
            default:
                return LEGACY_FORMAT_OPENAI;
        }
    }
}
