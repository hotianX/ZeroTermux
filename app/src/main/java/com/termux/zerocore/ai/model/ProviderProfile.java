package com.termux.zerocore.ai.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ProviderProfile {
    private long id;
    private String name;
    private String formatType; // "deepseek", "openai", "claude", "gemini"
    private String apiUrl;
    private String apiKey;
    private String modelName;
    private boolean isDefault;
    private String optionsJson;

    public ProviderProfile() {
    }

    public ProviderProfile(long id, String name, String formatType, String apiUrl,
                           String apiKey, String modelName, boolean isDefault) {
        this(id, name, formatType, apiUrl, apiKey, modelName, isDefault, "");
    }

    public ProviderProfile(long id, String name, String formatType, String apiUrl,
                           String apiKey, String modelName, boolean isDefault,
                           String optionsJson) {
        this.id = id;
        this.name = name;
        this.formatType = formatType;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.isDefault = isDefault;
        this.optionsJson = optionsJson;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFormatType() { return formatType; }
    public void setFormatType(String formatType) { this.formatType = formatType; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }

    public boolean isDeepSeekThinkingEnabled() {
        return getBooleanOption("thinking_enabled", true);
    }

    public String getDeepSeekReasoningEffort() {
        String effort = getStringOption("reasoning_effort", "high");
        return "max".equals(effort) ? "max" : "high";
    }

    public String getDeepSeekToolsJson() {
        String toolsJson = getStringOption("tools_json", "");
        if (toolsJson != null && !toolsJson.trim().isEmpty()) {
            return toolsJson;
        }
        return getJsonOption("tools");
    }

    public String getDeepSeekToolChoiceJson() {
        return getJsonOption("tool_choice");
    }

    private boolean getBooleanOption(String key, boolean defaultValue) {
        try {
            JsonObject options = parseOptions();
            if (options != null && options.has(key) && !options.get(key).isJsonNull()) {
                return options.get(key).getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private String getStringOption(String key, String defaultValue) {
        try {
            JsonObject options = parseOptions();
            if (options != null && options.has(key) && !options.get(key).isJsonNull()) {
                return options.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private String getJsonOption(String key) {
        try {
            JsonObject options = parseOptions();
            if (options != null && options.has(key) && !options.get(key).isJsonNull()) {
                return options.get(key).toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private JsonObject parseOptions() {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return null;
        }
        return JsonParser.parseString(optionsJson).getAsJsonObject();
    }
}
