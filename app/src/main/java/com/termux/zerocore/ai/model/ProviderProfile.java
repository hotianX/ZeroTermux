package com.termux.zerocore.ai.model;

public class ProviderProfile {
    private long id;
    private String name;
    private String formatType; // legacy display/router value: "openai", "claude", "gemini"
    private String protocol;
    private String endpointPathPolicy;
    private String authMode;
    private String endpointPathPolicySource;
    private String authModeSource;
    private String apiUrl;
    private String apiKey;
    private String modelName;
    private boolean isDefault;
    private String capabilitiesJson;
    private String advancedParamsJson;
    private boolean reasoningEnabled;
    private String reasoningEffort;

    public ProviderProfile() {
    }

    public ProviderProfile(long id, String name, String formatType, String apiUrl,
                           String apiKey, String modelName, boolean isDefault) {
        this(id, name, ProviderProfileContract.normalizeProtocol(formatType),
            ProviderProfileContract.defaultEndpointPolicy(ProviderProfileContract.normalizeProtocol(formatType)),
            ProviderProfileContract.defaultAuthMode(ProviderProfileContract.normalizeProtocol(formatType)),
            apiUrl, apiKey, modelName, isDefault, "", "", false, "");
        this.formatType = ProviderProfileContract.legacyFormatForProtocol(this.protocol);
    }

    public ProviderProfile(long id, String name, String protocol, String endpointPathPolicy,
                           String authMode, String apiUrl, String apiKey, String modelName,
                           boolean isDefault, String capabilitiesJson, String advancedParamsJson,
                           boolean reasoningEnabled, String reasoningEffort) {
        this.id = id;
        this.name = name;
        this.protocol = ProviderProfileContract.normalizeProtocol(protocol);
        this.formatType = ProviderProfileContract.legacyFormatForProtocol(this.protocol);
        this.endpointPathPolicy = endpointPathPolicy == null || endpointPathPolicy.trim().isEmpty()
            ? ProviderProfileContract.defaultEndpointPolicy(this.protocol)
            : endpointPathPolicy.trim();
        this.endpointPathPolicySource = endpointPathPolicy;
        this.authMode = authMode == null || authMode.trim().isEmpty()
            ? ProviderProfileContract.defaultAuthMode(this.protocol)
            : authMode.trim();
        this.authModeSource = authMode;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.isDefault = isDefault;
        this.capabilitiesJson = capabilitiesJson;
        this.advancedParamsJson = advancedParamsJson;
        this.reasoningEnabled = reasoningEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    public static ProviderProfile deepSeekDefault(String apiKey) {
        return new ProviderProfile(0, "DeepSeek", ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
            ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT,
            ProviderProfileContract.AUTH_BEARER_AUTHORIZATION,
            ProviderProfileContract.DEEPSEEK_BASE_URL,
            apiKey != null ? apiKey : "",
            ProviderProfileContract.DEEPSEEK_DEFAULT_MODEL,
            true,
            "{\"streaming\":true,\"thinking_reasoning\":true,\"system_prompt\":true,\"model_list\":true,\"tools_function_calling\":true,\"advanced_sampling\":true}",
            "{}",
            false,
            "");
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFormatType() {
        if (formatType == null || formatType.trim().isEmpty()) {
            return ProviderProfileContract.legacyFormatForProtocol(getProtocol());
        }
        return formatType;
    }
    public void setFormatType(String formatType) {
        this.formatType = formatType;
        if (protocol == null || protocol.trim().isEmpty()) {
            this.protocol = ProviderProfileContract.normalizeProtocol(formatType);
        }
    }

    public String getProtocol() {
        return ProviderProfileContract.normalizeProtocol(protocol != null ? protocol : formatType);
    }
    public void setProtocol(String protocol) {
        this.protocol = ProviderProfileContract.normalizeProtocol(protocol);
        this.formatType = ProviderProfileContract.legacyFormatForProtocol(this.protocol);
    }

    public String getEndpointPathPolicy() {
        if (endpointPathPolicy == null || endpointPathPolicy.trim().isEmpty()) {
            return ProviderProfileContract.defaultEndpointPolicy(getProtocol());
        }
        return endpointPathPolicy;
    }
    public void setEndpointPathPolicy(String endpointPathPolicy) {
        this.endpointPathPolicySource = endpointPathPolicy;
        this.endpointPathPolicy = endpointPathPolicy == null || endpointPathPolicy.trim().isEmpty()
            ? ProviderProfileContract.defaultEndpointPolicy(getProtocol())
            : endpointPathPolicy.trim();
    }
    public String getEndpointPathPolicySource() { return endpointPathPolicySource; }

    public String getAuthMode() {
        if (authMode == null || authMode.trim().isEmpty()) {
            return ProviderProfileContract.defaultAuthMode(getProtocol());
        }
        return authMode;
    }
    public void setAuthMode(String authMode) {
        this.authModeSource = authMode;
        this.authMode = authMode == null || authMode.trim().isEmpty()
            ? ProviderProfileContract.defaultAuthMode(getProtocol())
            : authMode.trim();
    }
    public String getAuthModeSource() { return authModeSource; }

    public boolean hasUnknownEndpointPathPolicy() {
        return endpointPathPolicySource != null && !endpointPathPolicySource.trim().isEmpty()
            && !ProviderProfileContract.isKnownEndpointPolicy(endpointPathPolicySource);
    }

    public boolean hasUnknownAuthMode() {
        return authModeSource != null && !authModeSource.trim().isEmpty()
            && !ProviderProfileContract.isKnownAuthMode(authModeSource);
    }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey != null ? apiKey.trim() : ""; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }

    public String getAdvancedParamsJson() { return advancedParamsJson; }
    public void setAdvancedParamsJson(String advancedParamsJson) { this.advancedParamsJson = advancedParamsJson; }

    public boolean isReasoningEnabled() { return reasoningEnabled; }
    public void setReasoningEnabled(boolean reasoningEnabled) { this.reasoningEnabled = reasoningEnabled; }

    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }

    public boolean isDeepSeekPolicy() {
        return ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT.equals(getEndpointPathPolicy());
    }
}
