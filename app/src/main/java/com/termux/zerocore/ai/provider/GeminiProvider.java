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

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/** Google Gemini generateContent provider. Defaults to x-goog-api-key header auth. */
public class GeminiProvider implements AIProvider {

    @Override
    public String getFormatType() { return ProviderProfileContract.LEGACY_FORMAT_GEMINI; }

    @Override
    public String getDisplayName() { return "Google Gemini"; }

    @Override
    public Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                                String systemPrompt, boolean stream) {
        validateProfile(profile);
        JsonArray contentsArray = new JsonArray();
        for (RequestMessageItem item : messages) {
            if (RequestMessageItem.ROLE_SYSTEM.equals(item.role)) continue;
            JsonObject content = new JsonObject();
            String role = RequestMessageItem.ROLE_ASSISTANT.equals(item.role) ? "model" : "user";
            content.addProperty("role", role);
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", item.content);
            parts.add(textPart);
            content.add("parts", parts);
            contentsArray.add(content);
        }

        JsonObject body = new JsonObject();
        body.add("contents", contentsArray);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInstruction = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysTextPart = new JsonObject();
            sysTextPart.addProperty("text", systemPrompt);
            sysParts.add(sysTextPart);
            sysInstruction.add("parts", sysParts);
            body.add("systemInstruction", sysInstruction);
        }

        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder()
            .url(buildGenerateContentUrl(profile, stream))
            .addHeader("Content-Type", "application/json")
            .post(requestBody);
        if (!ProviderProfileContract.AUTH_QUERY_KEY_COMPAT_ONLY.equals(profile.getAuthMode())) {
            builder.addHeader("x-goog-api-key", profile.getApiKey() != null ? profile.getApiKey().trim() : "");
        }
        return builder.build();
    }

    public static String buildGenerateContentUrl(ProviderProfile profile, boolean stream) {
        validateProfile(profile);
        String baseUrl = normalizeBaseUrl(profile != null ? profile.getApiUrl() : "");
        String model = profile != null ? profile.getModelName() : "";
        String action = stream ? "streamGenerateContent" : "generateContent";
        String endpoint = appendPath(baseUrl, "v1beta/models/" + model + ":" + action);
        HttpUrl parsed = HttpUrl.parse(endpoint);
        if (parsed == null) return endpoint;
        HttpUrl.Builder urlBuilder = parsed.newBuilder();
        if (stream) urlBuilder.addQueryParameter("alt", "sse");
        if (profile != null && ProviderProfileContract.AUTH_QUERY_KEY_COMPAT_ONLY.equals(profile.getAuthMode())) {
            urlBuilder.addQueryParameter("key", profile.getApiKey() != null ? profile.getApiKey().trim() : "");
        }
        return urlBuilder.build().toString();
    }

    public static String normalizeBaseUrl(String rawUrl) {
        String base = rawUrl == null ? "" : rawUrl.trim();
        int query = base.indexOf('?');
        if (query >= 0) base = base.substring(0, query);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String lower = base.toLowerCase();
        String[] markers = new String[]{"/v1beta/models/", "/models/"};
        for (String marker : markers) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                base = base.substring(0, idx);
                break;
            }
        }
        lower = base.toLowerCase();
        if (lower.endsWith("/v1beta")) base = base.substring(0, base.length() - "/v1beta".length());
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String appendPath(String base, String path) {
        if (base == null || base.isEmpty()) return path;
        return base + "/" + path;
    }

    private static void validateProfile(ProviderProfile profile) {
        if (profile == null) return;
        if (profile.hasUnknownEndpointPathPolicy()) {
            throw new IllegalArgumentException("Unsupported Gemini endpoint policy: " + profile.getEndpointPathPolicySource());
        }
        if (profile.hasUnknownAuthMode()) {
            throw new IllegalArgumentException("Unsupported Gemini auth mode: " + profile.getAuthModeSource());
        }
        if (!ProviderProfileContract.ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT.equals(profile.getEndpointPathPolicy())) {
            throw new IllegalArgumentException("Unsupported Gemini endpoint policy: " + profile.getEndpointPathPolicy());
        }
        String authMode = profile.getAuthMode();
        if (!ProviderProfileContract.AUTH_GOOGLE_X_GOOG_API_KEY.equals(authMode)
            && !ProviderProfileContract.AUTH_QUERY_KEY_COMPAT_ONLY.equals(authMode)) {
            throw new IllegalArgumentException("Unsupported Gemini auth mode: " + authMode);
        }
    }

    @Override
    public String parseResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            StringBuilder result = new StringBuilder();
            if (candidates != null && candidates.size() > 0) {
                appendCandidate(result, candidates.get(0).getAsJsonObject());
            }
            return result.toString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Gemini response", e);
        }
    }

    private void appendCandidate(StringBuilder result, JsonObject candidate) {
        JsonObject content = candidate.getAsJsonObject("content");
        if (content == null || !content.has("parts")) return;
        JsonArray parts = content.getAsJsonArray("parts");
        for (JsonElement element : parts) {
            JsonObject part = element.getAsJsonObject();
            if (part.has("text")) result.append(part.get("text").getAsString());
            if (part.has("functionCall")) {
                result.append("\n\n[").append(toolDisplayOnlyNotice()).append("]\n```json\n")
                    .append(part.get("functionCall").toString()).append("\n```\n");
            }
        }
    }


    @Override
    public List<ProviderStreamEvent> parseResponseEvents(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!json.has("candidates")) return Collections.emptyList();
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return Collections.emptyList();
            List<ProviderStreamEvent> events = eventsFromCandidate(candidates.get(0).getAsJsonObject());
            return events;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Gemini response", e);
        }
    }

    @Override
    public String parseStreamChunk(String line) throws AIProviderException {
        StringBuilder content = new StringBuilder();
        for (ProviderStreamEvent event : parseStreamEvents(line)) {
            if (ProviderStreamEvent.TYPE_CONTENT_DELTA.equals(event.getType()) && event.getTextDelta() != null) content.append(event.getTextDelta());
            else if (ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(event.getType())) {
                content.append("\n\n[").append(toolDisplayOnlyNotice()).append("]\n```json\n{\"name\":\"")
                    .append(event.getToolName() != null ? event.getToolName() : "")
                    .append("\",\"arguments_delta\":")
                    .append(event.getToolArgumentsDelta() != null ? event.getToolArgumentsDelta() : "{}")
                    .append("}\n```\n");
            }
        }
        return content.length() == 0 ? null : content.toString();
    }

    @Override
    public List<ProviderStreamEvent> parseStreamEvents(String line) throws AIProviderException {
        if (line == null || !line.startsWith("data: ")) return Collections.emptyList();
        String cleanLine = line.substring(6);
        if (cleanLine.isEmpty() || cleanLine.charAt(0) != '{') return Collections.emptyList();
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            if (!json.has("candidates")) return Collections.emptyList();
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates.size() == 0) return Collections.emptyList();
            return eventsFromCandidate(candidates.get(0).getAsJsonObject());
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Gemini stream chunk", e);
        }
    }

    private List<ProviderStreamEvent> eventsFromCandidate(JsonObject candidate) {
        List<ProviderStreamEvent> events = new ArrayList<>();
        JsonObject content = candidate.getAsJsonObject("content");
        if (content != null && content.has("parts")) {
            JsonArray parts = content.getAsJsonArray("parts");
            for (JsonElement element : parts) {
                JsonObject part = element.getAsJsonObject();
                if (part.has("text")) events.add(ProviderStreamEvent.content(part.get("text").getAsString()));
                if (part.has("functionCall")) {
                    JsonObject call = part.getAsJsonObject("functionCall");
                    events.add(ProviderStreamEvent.toolCall(null,
                        call.has("name") ? call.get("name").getAsString() : null,
                        call.has("args") ? call.get("args").toString() : null));
                }
            }
        }
        if (candidate.has("finishReason")) events.add(ProviderStreamEvent.done(candidate.get("finishReason").getAsString()));
        return events;
    }

    @Override
    public boolean isStreamComplete(String line) { return false; }

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
        } catch (Exception ignored) {}
        if (providerMessage == null || providerMessage.isEmpty()) providerMessage = responseBody;
        return ProviderErrorUtils.formatError(statusCode, providerMessage);
    }
}
