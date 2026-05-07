package com.termux.zerocore.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.termux.zerocore.ai.model.AIProviderException;
import com.termux.zerocore.ai.model.ProviderProfile;
import com.termux.zerocore.llm.model.RequestMessageItem;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * DeepSeek Chat Completions provider.
 * DeepSeek is OpenAI-compatible, but exposes thinking-mode fields such as
 * reasoning_content, thinking.type, and reasoning_effort.
 */
public class DeepSeekProvider implements AIProvider {
    private JsonArray streamedToolCalls = new JsonArray();

    @Override
    public String getFormatType() {
        return "deepseek";
    }

    @Override
    public String getDisplayName() {
        return "DeepSeek";
    }

    @Override
    public Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                                String systemPrompt, boolean stream) {
        streamedToolCalls = new JsonArray();
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
            msg.addProperty("content", item.content == null ? "" : item.content);
            boolean isAssistant = RequestMessageItem.ROLE_ASSISTANT.equals(item.role);
            if (isAssistant && item.reasoningContent != null && !item.reasoningContent.isEmpty()) {
                msg.addProperty("reasoning_content", item.reasoningContent);
            }
            if (item.toolCallId != null && !item.toolCallId.isEmpty()) {
                msg.addProperty("tool_call_id", item.toolCallId);
            }
            if (isAssistant && item.toolCallsJson != null && !item.toolCallsJson.isEmpty()) {
                try {
                    msg.add("tool_calls", JsonParser.parseString(item.toolCallsJson).getAsJsonArray());
                } catch (Exception ignored) {
                }
            }
            messagesArray.add(msg);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", profile.getModelName());
        body.add("messages", messagesArray);
        body.addProperty("stream", stream);

        JsonObject thinking = new JsonObject();
        boolean thinkingEnabled = profile.isDeepSeekThinkingEnabled();
        thinking.addProperty("type", thinkingEnabled ? "enabled" : "disabled");
        body.add("thinking", thinking);
        if (thinkingEnabled) {
            body.addProperty("reasoning_effort", profile.getDeepSeekReasoningEffort());
        }
        addRawJsonIfPresent(body, "tools", profile.getDeepSeekToolsJson());
        addRawJsonIfPresent(body, "tool_choice", profile.getDeepSeekToolChoiceJson());

        RequestBody requestBody = RequestBody.create(
            body.toString(),
            MediaType.parse("application/json; charset=utf-8"));

        return new Request.Builder()
            .url(getChatCompletionsUrl(profile.getApiUrl()))
            .addHeader("Authorization", "Bearer " + profile.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build();
    }

    @Override
    public String parseResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonElement content = json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content");
            return content == null || content.isJsonNull() ? "" : content.getAsString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse DeepSeek response", e);
        }
    }

    @Override
    public String parseStreamChunk(String line) throws AIProviderException {
        JsonObject delta = parseDelta(line);
        if (delta == null || !delta.has("content") || delta.get("content").isJsonNull()) {
            return null;
        }
        return delta.get("content").getAsString();
    }

    @Override
    public String parseReasoningStreamChunk(String line) throws AIProviderException {
        JsonObject delta = parseDelta(line);
        if (delta == null || !delta.has("reasoning_content") || delta.get("reasoning_content").isJsonNull()) {
            return null;
        }
        return delta.get("reasoning_content").getAsString();
    }

    @Override
    public String parseToolCallsStreamChunk(String line) throws AIProviderException {
        JsonObject delta = parseDelta(line);
        if (delta == null || !delta.has("tool_calls") || delta.get("tool_calls").isJsonNull()) {
            return null;
        }
        JsonArray toolCallDeltas = delta.getAsJsonArray("tool_calls");
        for (JsonElement item : toolCallDeltas) {
            if (item != null && item.isJsonObject()) {
                mergeToolCallDelta(item.getAsJsonObject());
            }
        }
        return streamedToolCalls.size() == 0 ? null : streamedToolCalls.toString();
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

    public Request buildFimCompletionRequest(ProviderProfile profile, String prompt, String suffix,
                                             boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-v4-pro");
        body.addProperty("prompt", prompt == null ? "" : prompt);
        if (suffix != null) {
            body.addProperty("suffix", suffix);
        }
        body.addProperty("stream", stream);

        RequestBody requestBody = RequestBody.create(
            body.toString(),
            MediaType.parse("application/json; charset=utf-8"));

        return new Request.Builder()
            .url(getFimCompletionsUrl(profile.getApiUrl()))
            .addHeader("Authorization", "Bearer " + profile.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build();
    }

    public String parseFimCompletionResponse(String responseBody) throws AIProviderException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse DeepSeek FIM response", e);
        }
    }

    public String parseFimCompletionStreamChunk(String line) throws AIProviderException {
        String cleanLine = line.startsWith("data: ") ? line.substring(6) : line;
        if (cleanLine.isEmpty() || cleanLine.charAt(0) != '{') {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return null;
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (!choice.has("text") || choice.get("text").isJsonNull()) return null;
            return choice.get("text").getAsString();
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse DeepSeek FIM stream chunk", e);
        }
    }

    private void addRawJsonIfPresent(JsonObject body, String key, String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return;
        }
        try {
            body.add(key, JsonParser.parseString(rawJson));
        } catch (Exception ignored) {
        }
    }

    private void mergeToolCallDelta(JsonObject delta) {
        int index = delta.has("index") && !delta.get("index").isJsonNull()
            ? delta.get("index").getAsInt()
            : streamedToolCalls.size();
        while (streamedToolCalls.size() <= index) {
            streamedToolCalls.add(new JsonObject());
        }

        JsonObject target = streamedToolCalls.get(index).getAsJsonObject();
        if (delta.has("id") && !delta.get("id").isJsonNull()) {
            target.add("id", delta.get("id"));
        }
        if (delta.has("type") && !delta.get("type").isJsonNull()) {
            target.add("type", delta.get("type"));
        }
        if (delta.has("function") && delta.get("function").isJsonObject()) {
            mergeFunctionDelta(target, delta.getAsJsonObject("function"));
        }
    }

    private void mergeFunctionDelta(JsonObject target, JsonObject functionDelta) {
        JsonObject function = target.has("function") && target.get("function").isJsonObject()
            ? target.getAsJsonObject("function")
            : new JsonObject();
        target.add("function", function);

        if (functionDelta.has("name") && !functionDelta.get("name").isJsonNull()) {
            String name = functionDelta.get("name").getAsString();
            if (!name.isEmpty()) {
                function.addProperty("name", name);
            }
        }
        if (functionDelta.has("arguments") && !functionDelta.get("arguments").isJsonNull()) {
            String existing = function.has("arguments") && !function.get("arguments").isJsonNull()
                ? function.get("arguments").getAsString()
                : "";
            function.addProperty("arguments", existing + functionDelta.get("arguments").getAsString());
        }
    }

    private JsonObject parseDelta(String line) throws AIProviderException {
        String cleanLine = line.startsWith("data: ") ? line.substring(6) : line;
        if (cleanLine.isEmpty() || cleanLine.charAt(0) != '{') {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(cleanLine).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return null;
            return choices.get(0).getAsJsonObject().getAsJsonObject("delta");
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse DeepSeek stream chunk", e);
        }
    }

    private String getChatCompletionsUrl(String apiUrl) {
        String url = apiUrl == null || apiUrl.trim().isEmpty()
            ? "https://api.deepseek.com/chat/completions"
            : apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }

    private String getFimCompletionsUrl(String apiUrl) {
        String url = apiUrl == null || apiUrl.trim().isEmpty()
            ? "https://api.deepseek.com/beta/completions"
            : apiUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/beta/completions")) {
            return url;
        }
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        } else if (url.endsWith("/completions")) {
            url = url.substring(0, url.length() - "/completions".length());
        }
        if (url.endsWith("/beta")) {
            return url + "/completions";
        }
        return url + "/beta/completions";
    }
}
