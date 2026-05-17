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

/** Anthropic Claude Messages API provider. */
public class ClaudeProvider implements AIProvider {
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public String getFormatType() { return ProviderProfileContract.LEGACY_FORMAT_CLAUDE; }

    @Override
    public String getDisplayName() { return "Anthropic Claude"; }

    @Override
    public Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                                String systemPrompt, boolean stream) {
        validateProfile(profile);
        JsonArray messagesArray = new JsonArray();
        for (RequestMessageItem item : messages) {
            if (RequestMessageItem.ROLE_SYSTEM.equals(item.role)) continue;
            JsonObject msg = new JsonObject();
            msg.addProperty("role", item.role);
            msg.addProperty("content", item.content);
            messagesArray.add(msg);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", profile.getModelName());
        body.addProperty("max_tokens", 4096);
        if (systemPrompt != null && !systemPrompt.isEmpty()) body.addProperty("system", systemPrompt);
        body.add("messages", messagesArray);
        body.addProperty("stream", stream);
        if (profile.isReasoningEnabled()) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            thinking.addProperty("budget_tokens", 1024);
            body.add("thinking", thinking);
        }

        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder()
            .url(buildMessagesUrl(profile))
            .addHeader("x-api-key", profile.getApiKey() != null ? profile.getApiKey().trim() : "")
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build();
    }

    public static String buildMessagesUrl(ProviderProfile profile) {
        validateProfile(profile);
        String base = normalizeBaseUrl(profile != null ? profile.getApiUrl() : "");
        return appendPath(base, "v1/messages");
    }

    public static String normalizeBaseUrl(String rawUrl) {
        String base = rawUrl == null ? "" : rawUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String lower = base.toLowerCase();
        if (lower.endsWith("/v1/messages")) base = base.substring(0, base.length() - "/v1/messages".length());
        else if (lower.endsWith("/messages")) base = base.substring(0, base.length() - "/messages".length());
        else if (lower.endsWith("/v1")) base = base.substring(0, base.length() - "/v1".length());
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
            throw new IllegalArgumentException("Unsupported Claude endpoint policy: " + profile.getEndpointPathPolicySource());
        }
        if (profile.hasUnknownAuthMode()) {
            throw new IllegalArgumentException("Unsupported Claude auth mode: " + profile.getAuthModeSource());
        }
        if (!ProviderProfileContract.ENDPOINT_CLAUDE_V1_MESSAGES.equals(profile.getEndpointPathPolicy())) {
            throw new IllegalArgumentException("Unsupported Claude endpoint policy: " + profile.getEndpointPathPolicy());
        }
        if (!ProviderProfileContract.AUTH_ANTHROPIC_X_API_KEY.equals(profile.getAuthMode())) {
            throw new IllegalArgumentException("Unsupported Claude auth mode: " + profile.getAuthMode());
        }
    }

    @Override
    public String parseResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray content = json.getAsJsonArray("content");
            StringBuilder result = new StringBuilder();
            if (content != null) {
                for (JsonElement element : content) appendContentBlock(result, element.getAsJsonObject());
            }
            return result.toString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Claude response", e);
        }
    }

    private void appendContentBlock(StringBuilder result, JsonObject block) {
        String type = block.has("type") ? block.get("type").getAsString() : "";
        if ("text".equals(type) && block.has("text")) result.append(block.get("text").getAsString());
        else if (("thinking".equals(type) || "redacted_thinking".equals(type)) && block.has("thinking")) {
            result.append("\n\n<details><summary>Reasoning</summary>\n")
                .append(block.get("thinking").getAsString()).append("\n</details>\n");
        } else if ("tool_use".equals(type)) {
            result.append("\n\n[").append(toolDisplayOnlyNotice()).append("]\n```json\n")
                .append(block.toString()).append("\n```\n");
        }
    }


    @Override
    public List<ProviderStreamEvent> parseResponseEvents(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray content = json.getAsJsonArray("content");
            List<ProviderStreamEvent> events = new ArrayList<>();
            if (content != null) {
                for (JsonElement element : content) {
                    JsonObject block = element.getAsJsonObject();
                    String type = block.has("type") ? block.get("type").getAsString() : "";
                    if ("text".equals(type) && block.has("text")) {
                        events.add(ProviderStreamEvent.content(block.get("text").getAsString()));
                    } else if (("thinking".equals(type) || "redacted_thinking".equals(type)) && block.has("thinking")) {
                        events.add(ProviderStreamEvent.reasoning(block.get("thinking").getAsString()));
                    } else if ("tool_use".equals(type)) {
                        events.add(ProviderStreamEvent.toolCall(
                            block.has("id") ? block.get("id").getAsString() : null,
                            block.has("name") ? block.get("name").getAsString() : null,
                            block.has("input") ? block.get("input").toString() : null));
                    }
                }
            }
            events.add(ProviderStreamEvent.done(json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
                ? json.get("stop_reason").getAsString() : "stop"));
            return events;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Claude response", e);
        }
    }

    @Override
    public String parseStreamChunk(String line) throws AIProviderException {
        StringBuilder content = new StringBuilder();
        for (ProviderStreamEvent event : parseStreamEvents(line)) {
            if (ProviderStreamEvent.TYPE_CONTENT_DELTA.equals(event.getType()) && event.getTextDelta() != null) content.append(event.getTextDelta());
            else if (ProviderStreamEvent.TYPE_REASONING_DELTA.equals(event.getType()) && event.getReasoningDelta() != null) content.append(event.getReasoningDelta());
            else if (ProviderStreamEvent.TYPE_TOOL_CALL_DELTA.equals(event.getType())) {
                content.append("\n\n[").append(toolDisplayOnlyNotice()).append("]\n```json\n{\"name\":\"")
                    .append(event.getToolName() != null ? event.getToolName() : "")
                    .append("\",\"arguments_delta\":\"")
                    .append(event.getToolArgumentsDelta() != null ? event.getToolArgumentsDelta().replace("\"", "\\\"") : "")
                    .append("\"}\n```\n");
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
            String type = json.has("type") ? json.get("type").getAsString() : "";
            List<ProviderStreamEvent> events = new ArrayList<>();
            if ("message_stop".equals(type)) events.add(ProviderStreamEvent.done("message_stop"));
            if ("content_block_delta".equals(type)) {
                JsonObject delta = json.getAsJsonObject("delta");
                if (delta != null) {
                    String deltaType = delta.has("type") ? delta.get("type").getAsString() : "";
                    if (delta.has("text")) events.add(ProviderStreamEvent.content(delta.get("text").getAsString()));
                    if (delta.has("thinking")) events.add(ProviderStreamEvent.reasoning(delta.get("thinking").getAsString()));
                    if ("thinking_delta".equals(deltaType) && delta.has("thinking")) events.add(ProviderStreamEvent.reasoning(delta.get("thinking").getAsString()));
                    if ("signature_delta".equals(deltaType) && delta.has("signature")) {
                        events.add(new ProviderStreamEvent.Builder(ProviderStreamEvent.TYPE_RAW_DEBUG_REDACTED)
                            .providerEventName("signature_delta").redactedRawPayload("<redacted-provider-signature>").build());
                    }
                    if ("input_json_delta".equals(deltaType)) {
                        events.add(ProviderStreamEvent.toolCall(null, null, delta.has("partial_json") ? delta.get("partial_json").getAsString() : null));
                    }
                }
            } else if ("content_block_start".equals(type) && json.has("content_block")) {
                JsonObject block = json.getAsJsonObject("content_block");
                if ("tool_use".equals(block.has("type") ? block.get("type").getAsString() : "")) {
                    events.add(ProviderStreamEvent.toolCall(
                        block.has("id") ? block.get("id").getAsString() : null,
                        block.has("name") ? block.get("name").getAsString() : null,
                        block.has("input") ? block.get("input").toString() : null));
                }
            }
            return events;
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse Claude stream chunk", e);
        }
    }

    @Override
    public boolean isStreamComplete(String line) {
        if (line == null || !line.startsWith("data: ")) return false;
        String cleanLine = line.substring(6);
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            return "message_stop".equals(json.has("type") ? json.get("type").getAsString() : "");
        } catch (Exception e) {
            return false;
        }
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
        } catch (Exception ignored) {}
        if (providerMessage == null || providerMessage.isEmpty()) providerMessage = responseBody;
        return ProviderErrorUtils.formatError(statusCode, providerMessage);
    }
}
