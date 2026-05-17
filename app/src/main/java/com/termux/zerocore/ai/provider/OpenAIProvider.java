package com.termux.zerocore.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.termux.zerocore.ai.llm.model.RequestMessageItem;
import com.termux.zerocore.ai.model.AIProviderException;
import com.termux.zerocore.ai.model.ProviderProfile;
import com.termux.zerocore.ai.model.ProviderProfileContract;
import com.termux.zerocore.ai.model.ProviderStreamEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** OpenAI-compatible provider. DeepSeek is selected by endpointPathPolicy, not name/url heuristics. */
public class OpenAIProvider implements AIProvider {
    private static final String CHAT_COMPLETIONS_PATH = "chat/completions";
    private static final String MODELS_PATH = "models";
    private static final String V1_PREFIX = "v1";

    @Override
    public String getFormatType() {
        return ProviderProfileContract.LEGACY_FORMAT_OPENAI;
    }

    @Override
    public String getDisplayName() {
        return "OpenAI Compatible";
    }

    @Override
    public Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                                String systemPrompt, boolean stream) {
        validateAuth(profile);
        JsonArray messagesArray = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", RequestMessageItem.ROLE_SYSTEM);
            systemMsg.addProperty("content", systemPrompt);
            messagesArray.add(systemMsg);
        }
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
        if (profile.isReasoningEnabled()) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            body.add("thinking", thinking);
            if (profile.getReasoningEffort() != null && !profile.getReasoningEffort().isEmpty()) {
                body.addProperty("reasoning_effort", profile.getReasoningEffort());
            }
        } else if (ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT.equals(profile.getEndpointPathPolicy())) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "disabled");
            body.add("thinking", thinking);
        }

        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder()
            .url(buildChatCompletionsUrl(profile))
            .addHeader("Authorization", "Bearer " + safeKey(profile))
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build();
    }

    public static String buildChatCompletionsUrl(ProviderProfile profile) {
        return buildEndpointUrl(profile, CHAT_COMPLETIONS_PATH);
    }

    public Request buildModelsRequest(ProviderProfile profile) {
        validateAuth(profile);
        return new Request.Builder()
            .url(buildModelsUrl(profile))
            .addHeader("Authorization", "Bearer " + safeKey(profile))
            .addHeader("Accept", "application/json")
            .get()
            .build();
    }

    public static String buildModelsUrl(ProviderProfile profile) {
        return buildEndpointUrl(profile, MODELS_PATH);
    }

    public List<String> parseModelsResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");
            if (data == null) return Collections.emptyList();
            List<String> models = new ArrayList<>();
            for (JsonElement element : data) {
                JsonObject model = element.getAsJsonObject();
                if (hasString(model, "id")) models.add(model.get("id").getAsString());
            }
            return models;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI-compatible models response", e);
        }
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String errorMessage);
    }

    public void listModels(ProviderProfile profile, okhttp3.OkHttpClient client, ModelsCallback callback) {
        client.newCall(buildModelsRequest(profile)).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        callback.onSuccess(parseModelsResponse(body));
                    } catch (AIProviderException e) {
                        callback.onError(e.getMessage());
                    }
                } else {
                    callback.onError(parseError(response.code(), body));
                }
            }
        });
    }

    private static String buildEndpointUrl(ProviderProfile profile, String endpointPath) {
        if (profile != null && profile.hasUnknownEndpointPathPolicy()) {
            throw new IllegalArgumentException("Unsupported OpenAI-compatible endpoint policy: " + profile.getEndpointPathPolicySource());
        }
        String policy = profile != null ? profile.getEndpointPathPolicy() : ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT;
        String baseUrl = normalizeApiBaseUrl(profile != null ? profile.getApiUrl() : "", policy);
        if (ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT.equals(policy) && !endsWithPathSegment(baseUrl, V1_PREFIX)) {
            baseUrl = appendPath(baseUrl, V1_PREFIX);
        }
        return appendPath(baseUrl, endpointPath);
    }

    public static String normalizeApiBaseUrl(String rawUrl, String endpointPathPolicy) {
        if (endpointPathPolicy != null && !endpointPathPolicy.trim().isEmpty()
            && !ProviderProfileContract.isKnownEndpointPolicy(endpointPathPolicy)) {
            throw new IllegalArgumentException("Unsupported endpoint policy: " + endpointPathPolicy);
        }
        String baseUrl = rawUrl == null ? "" : rawUrl.trim();
        if (baseUrl.isEmpty()) return "";
        baseUrl = trimTrailingSlashes(baseUrl);
        boolean deepSeek = ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT.equals(endpointPathPolicy);

        String[] suffixes = deepSeek
            ? new String[]{"/v1/chat/completions", "/v1/models", "/chat/completions", "/models", "/v1"}
            : new String[]{"/v1/chat/completions", "/v1/models", "/chat/completions", "/models"};
        for (String suffix : suffixes) {
            if (baseUrl.toLowerCase().endsWith(suffix)) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - suffix.length());
                break;
            }
        }
        return trimTrailingSlashes(baseUrl);
    }

    private static String appendPath(String baseUrl, String path) {
        String cleanBase = trimTrailingSlashes(baseUrl);
        String cleanPath = path == null ? "" : path.trim();
        while (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
        if (cleanBase.isEmpty()) return cleanPath;
        if (cleanPath.isEmpty()) return cleanBase;
        return cleanBase + "/" + cleanPath;
    }

    private static String trimTrailingSlashes(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static boolean endsWithPathSegment(String url, String segment) {
        String cleanUrl = trimTrailingSlashes(url).toLowerCase();
        String cleanSegment = segment == null ? "" : segment.toLowerCase();
        return cleanUrl.equals(cleanSegment) || cleanUrl.endsWith("/" + cleanSegment);
    }

    private static String safeKey(ProviderProfile profile) {
        return profile != null && profile.getApiKey() != null ? profile.getApiKey().trim() : "";
    }

    private static void validateAuth(ProviderProfile profile) {
        if (profile != null && profile.hasUnknownAuthMode()) {
            throw new IllegalArgumentException("Unsupported OpenAI-compatible auth mode: " + profile.getAuthModeSource());
        }
        String authMode = profile != null ? profile.getAuthMode() : ProviderProfileContract.AUTH_BEARER_AUTHORIZATION;
        if (!ProviderProfileContract.AUTH_BEARER_AUTHORIZATION.equals(authMode)) {
            throw new IllegalArgumentException("Unsupported OpenAI-compatible auth mode: " + authMode);
        }
    }

    @Override
    public String parseResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return "";
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null) return "";
            StringBuilder result = new StringBuilder();
            appendIfPresent(result, message, "reasoning_content", true);
            appendIfPresent(result, message, "reasoning", true);
            appendIfPresent(result, message, "content", false);
            if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                result.append(formatToolDisplay(message.getAsJsonArray("tool_calls")));
            }
            return result.toString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI response", e);
        }
    }

    private void appendIfPresent(StringBuilder result, JsonObject object, String key, boolean reasoning) {
        if (object.has(key) && !object.get(key).isJsonNull()) {
            String value = object.get(key).getAsString();
            if (!value.isEmpty()) {
                if (reasoning) result.append("\n\n<details><summary>Reasoning</summary>\n").append(value).append("\n</details>\n");
                else result.append(value);
            }
        }
    }


    @Override
    public List<ProviderStreamEvent> parseResponseEvents(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return Collections.emptyList();
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            List<ProviderStreamEvent> events = new ArrayList<>();
            if (message != null) {
                if (hasString(message, "content")) events.add(ProviderStreamEvent.content(message.get("content").getAsString()));
                if (hasString(message, "reasoning_content")) events.add(ProviderStreamEvent.reasoning(message.get("reasoning_content").getAsString()));
                if (hasString(message, "reasoning")) events.add(ProviderStreamEvent.reasoning(message.get("reasoning").getAsString()));
                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    appendToolEvents(events, message.getAsJsonArray("tool_calls"));
                }
            }
            if (hasString(choice, "finish_reason")) events.add(ProviderStreamEvent.done(choice.get("finish_reason").getAsString()));
            return events;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI response", e);
        }
    }

    @Override
    public String parseStreamChunk(String line) throws AIProviderException {
        List<ProviderStreamEvent> events = parseStreamEvents(line);
        StringBuilder content = new StringBuilder();
        for (ProviderStreamEvent event : events) {
            if (ProviderStreamEvent.TYPE_CONTENT_DELTA.equals(event.getType()) && event.getTextDelta() != null) {
                content.append(event.getTextDelta());
            } else if (ProviderStreamEvent.TYPE_REASONING_DELTA.equals(event.getType()) && event.getReasoningDelta() != null) {
                content.append(event.getReasoningDelta());
            } else if (ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(event.getType())) {
                content.append(formatToolDisplay(event));
            }
        }
        return content.length() == 0 ? null : content.toString();
    }

    @Override
    public List<ProviderStreamEvent> parseStreamEvents(String line) throws AIProviderException {
        String cleanLine = line != null && line.startsWith("data: ") ? line.substring(6) : line;
        if (cleanLine == null || cleanLine.trim().isEmpty()) return Collections.emptyList();
        if ("[DONE]".equals(cleanLine.trim())) return Collections.singletonList(ProviderStreamEvent.done("done"));
        if (cleanLine.charAt(0) != '{') return Collections.emptyList();
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return Collections.emptyList();
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.getAsJsonObject("delta");
            List<ProviderStreamEvent> events = new ArrayList<>();
            if (delta != null) {
                if (hasString(delta, "content")) events.add(ProviderStreamEvent.content(delta.get("content").getAsString()));
                if (hasString(delta, "reasoning_content")) events.add(ProviderStreamEvent.reasoning(delta.get("reasoning_content").getAsString()));
                if (hasString(delta, "reasoning")) events.add(ProviderStreamEvent.reasoning(delta.get("reasoning").getAsString()));
                if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                    appendToolEvents(events, delta.getAsJsonArray("tool_calls"));
                }
            }
            if (hasString(choice, "finish_reason")) events.add(ProviderStreamEvent.done(choice.get("finish_reason").getAsString()));
            return events;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse OpenAI stream chunk", e);
        }
    }

    private void appendToolEvents(List<ProviderStreamEvent> events, JsonArray calls) {
        for (JsonElement el : calls) {
            JsonObject call = el.getAsJsonObject();
            JsonObject function = call.has("function") && call.get("function").isJsonObject()
                ? call.getAsJsonObject("function") : null;
            events.add(ProviderStreamEvent.toolCall(
                hasString(call, "id") ? call.get("id").getAsString() : null,
                function != null && hasString(function, "name") ? function.get("name").getAsString() : null,
                function != null && hasString(function, "arguments") ? function.get("arguments").getAsString() : null));
        }
    }

    private boolean hasString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).isJsonPrimitive();
    }

    @Override
    public boolean isStreamComplete(String line) {
        return line != null && "data: [DONE]".equals(line.trim());
    }

    private String formatToolDisplay(JsonArray calls) {
        return "\n\n[" + toolDisplayOnlyNotice() + "]\n```json\n" + calls.toString() + "\n```\n";
    }

    private String formatToolDisplay(ProviderStreamEvent event) {
        StringBuilder json = new StringBuilder("{");
        if (event.getToolCallId() != null) json.append("\"id\":\"").append(event.getToolCallId()).append("\",");
        if (event.getToolName() != null) json.append("\"name\":\"").append(event.getToolName()).append("\",");
        if (event.getToolArgumentsDelta() != null) json.append("\"arguments_delta\":\"").append(event.getToolArgumentsDelta().replace("\"", "\\\"")).append("\",");
        if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
        json.append("}");
        return "\n\n[" + toolDisplayOnlyNotice() + "]\n```json\n" + json + "\n```\n";
    }

    private String toolDisplayOnlyNotice() {
        return "\u5de5\u5177\u8c03\u7528\uff1a" + ProviderStreamEvent.TOOL_DISPLAY_ONLY_NOTICE;
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
        if (providerMessage == null || providerMessage.isEmpty()) providerMessage = responseBody;
        return ProviderErrorUtils.formatError(statusCode, providerMessage);
    }
}
