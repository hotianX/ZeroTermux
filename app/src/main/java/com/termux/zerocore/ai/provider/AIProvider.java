package com.termux.zerocore.ai.provider;

import com.termux.zerocore.ai.llm.model.RequestMessageItem;
import com.termux.zerocore.ai.model.AIProviderException;
import com.termux.zerocore.ai.model.ProviderProfile;
import com.termux.zerocore.ai.model.ProviderStreamEvent;

import java.util.Collections;
import java.util.List;

import okhttp3.Request;

/**
 * Interface for AI provider protocol implementations.
 * Each provider owns request construction and response/event parsing.
 */
public interface AIProvider {

    /** Unique legacy format identifier: "openai", "claude", "gemini" */
    String getFormatType();

    /** Display name: "OpenAI Compatible", "Anthropic Claude", "Google Gemini" */
    String getDisplayName();

    Request buildRequest(ProviderProfile profile, List<RequestMessageItem> messages,
                         String systemPrompt, boolean stream);

    /** Parse a non-streaming response body into assistant content */
    String parseResponse(String responseBody) throws AIProviderException;

    /** Parse structured events from a non-streaming response body. */
    default List<ProviderStreamEvent> parseResponseEvents(String responseBody) throws AIProviderException {
        String content = parseResponse(responseBody);
        if (content == null || content.isEmpty()) return Collections.emptyList();
        return Collections.singletonList(ProviderStreamEvent.content(content));
    }

    /** Parse structured events from a streaming chunk line. */
    default List<ProviderStreamEvent> parseStreamEvents(String line) throws AIProviderException {
        String content = parseStreamChunk(line);
        if (content == null || content.isEmpty()) return Collections.emptyList();
        return Collections.singletonList(ProviderStreamEvent.content(content));
    }

    /** Parse a streaming chunk line. Return content delta, or null if no content. */
    String parseStreamChunk(String line) throws AIProviderException;

    /** Return true if this line signals stream completion */
    boolean isStreamComplete(String line);

    /** Parse an error response into a user-friendly message */
    String parseError(int statusCode, String responseBody);
}
