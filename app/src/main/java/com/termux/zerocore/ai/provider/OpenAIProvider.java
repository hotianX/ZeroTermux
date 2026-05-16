package com.termux.zerocore.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.termux.zerocore.ai.llm.model.RequestMessageItem;
import com.termux.zerocore.ai.model.AIProviderException;
import com.termux.zerocore.ai.model.ProviderProfile;

import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * OpenAI-compatible provider. Covers DeepSeek, OpenAI, Groq, Ollama, vLLM, etc.
 */
public class OpenAIProvider implements AIProvider {

    private static final String CHAT_COMPLETIONS_PATH = "chat/completions";
    private static final String MODELS_PATH = "models";
    private static final String V1_PREFIX = "v1";

    @Override
    public String getFormatType() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI Compatible";
    }

    @Override
    public Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                                String systemPrompt, boolean stream) {
        JsonArray messagesArray = new JsonArray();

        // System prompt at position 0
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", RequestMessageItem.ROLE_SYSTEM);
            systemMsg.addProperty("content", systemPrompt);
            messagesArray.add(systemMsg);
        }

        // User/assistant messages
        for (RequestMessageItem item : messages) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", item.role);
            msg.addProperty("content", item.content);
            messagesArray.add(msg);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", profile.getModelName());
        body.add("messages", messagesArray);
        body.addProperty("stream", stream);

        RequestBody requestBody = RequestBody.create(
            body.toString(),
            MediaType.parse("application/json; charset=utf-8"));

        return new Request.Builder()
            .url(buildChatCompletionsUrl(profile))
            .addHeader("Authorization", "Bearer " + profile.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build();
    }

    /**
     * Build the chat completions URL from a configured API base URL. DeepSeek
     * official-compatible profiles use no /v1 prefix; other OpenAI-compatible
     * profiles keep the existing /v1 convention.
     */
    public static String buildChatCompletionsUrl(ProviderProfile profile) {
        return buildEndpointUrl(profile, CHAT_COMPLETIONS_PATH);
    }

    /**
     * Build a models-list request for DeepSeek/OpenAI-compatible providers.
     */
    public Request buildModelsRequest(ProviderProfile profile) {
        return new Request.Builder()
            .url(buildModelsUrl(profile))
            .addHeader("Authorization", "Bearer " + profile.getApiKey())
            .addHeader("Accept", "application/json")
            .get()
            .build();
    }

    /**
     * Build the models endpoint URL from the same base URL rules as chat.
     */
    public static String buildModelsUrl(ProviderProfile profile) {
        return buildEndpointUrl(profile, MODELS_PATH);
    }

    private static String buildEndpointUrl(ProviderProfile profile, String endpointPath) {
        boolean deepSeekOfficial = isDeepSeekProfile(profile);
        String baseUrl = normalizeApiBaseUrl(profile != null ? profile.getApiUrl() : "", deepSeekOfficial);
        if (!deepSeekOfficial && !endsWithPathSegment(baseUrl, V1_PREFIX)) {
            baseUrl = appendPath(baseUrl, V1_PREFIX);
        }
        return appendPath(baseUrl, endpointPath);
    }

    private static boolean isDeepSeekProfile(ProviderProfile profile) {
        if (profile == null) return false;
        String combined = String.valueOf(profile.getName()) + " "
            + String.valueOf(profile.getModelName()) + " "
            + String.valueOf(profile.getApiUrl());
        return combined.toLowerCase(Locale.ROOT).contains("deepseek");
    }

    private static String normalizeApiBaseUrl(String rawUrl, boolean deepSeekOfficial) {
        String baseUrl = rawUrl == null ? "" : rawUrl.trim();
        baseUrl = trimTrailingSlashes(baseUrl);

        String lowerUrl = baseUrl.toLowerCase(Locale.ROOT);
        String[] knownEndpointSuffixes = {
            "/v1/chat/completions",
            "/chat/completions",
            "/v1/models",
            "/models"
        };
        for (String suffix : knownEndpointSuffixes) {
            if (lowerUrl.endsWith(suffix)) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - suffix.length());
                break;
            }
        }

        baseUrl = trimTrailingSlashes(baseUrl);
        if (deepSeekOfficial && endsWithPathSegment(baseUrl, V1_PREFIX)) {
            if (baseUrl.equalsIgnoreCase(V1_PREFIX)) {
                baseUrl = "";
            } else {
                baseUrl = baseUrl.substring(0, baseUrl.length() - (V1_PREFIX.length() + 1));
            }
            baseUrl = trimTrailingSlashes(baseUrl);
        }
        return baseUrl;
    }

    private static String appendPath(String baseUrl, String path) {
        String cleanBase = trimTrailingSlashes(baseUrl);
        String cleanPath = path == null ? "" : path.trim();
        while (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanBase.isEmpty()) return cleanPath;
        if (cleanPath.isEmpty()) return cleanBase;
        return cleanBase + "/" + cleanPath;
    }

    private static String trimTrailingSlashes(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean endsWithPathSegment(String value, String segment) {
        if (value == null || segment == null || segment.isEmpty()) return false;
        String normalized = trimTrailingSlashes(value).toLowerCase(Locale.ROOT);
        String normalizedSegment = segment.toLowerCase(Locale.ROOT);
        return normalized.equals(normalizedSegment) || normalized.endsWith("/" + normalizedSegment);
    }

    @Override
    public String parseResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject message = json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message");
            if (message != null && message.has("content") && !message.get("content").isJsonNull()) {
                return message.get("content").getAsString();
            }
            return "";
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI response", e);
        }
    }

    @Override
    public String parseStreamChunk(String line) throws AIProviderException {
        // Strip "data: " prefix
        String cleanLine = line.startsWith("data: ") ? line.substring(6) : line;
        if (cleanLine.isEmpty() || cleanLine.charAt(0) != '{') {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices.size() == 0) return null;
            JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
            if (delta == null || !delta.has("content") || delta.get("content").isJsonNull()) return null;
            return delta.get("content").getAsString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI stream chunk", e);
        }
    }

    @Override
    public boolean isStreamComplete(String line) {
        return "data: [DONE]".equals(line.trim());
    }

    @Override
    public String parseError(int statusCode, String responseBody) {
        String providerMessage = null;
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                providerMessage = error.has("message") ? error.get("message").getAsString() : responseBody;
            }
        } catch (Exception ignored) {
        }
        if (providerMessage == null || providerMessage.isEmpty()) {
            providerMessage = responseBody;
        }
        return ProviderErrorUtils.formatError(statusCode, providerMessage);
    }
}
