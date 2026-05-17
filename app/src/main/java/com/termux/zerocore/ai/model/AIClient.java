package com.termux.zerocore.ai.model;

import com.example.xh_lib.utils.LogUtils;
import com.termux.zerocore.ai.llm.model.RequestMessageItem;
import com.termux.zerocore.ai.provider.AIProvider;
import com.termux.zerocore.ai.provider.ClaudeProvider;
import com.termux.zerocore.ai.provider.GeminiProvider;
import com.termux.zerocore.ai.provider.OpenAIProvider;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/** Generic AI HTTP client that delegates protocol details to AIProvider. */
public class AIClient {
    private static final String TAG = AIClient.class.getSimpleName();

    private static final OkHttpClient sharedClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public interface Listener {
        void onError(String errorMessage);
        void onMessage(String content);
        default void onStreamEvent(ProviderStreamEvent event) {}
        void onComplete();
    }

    public static AIProvider getProvider(String formatOrProtocol) {
        String protocol = ProviderProfileContract.normalizeProtocol(formatOrProtocol);
        switch (protocol) {
            case ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES:
                return new ClaudeProvider();
            case ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT:
                return new GeminiProvider();
            case ProviderProfileContract.PROTOCOL_OPENAI_CHAT:
            default:
                return new OpenAIProvider();
        }
    }

    public void ask(AIProvider provider, ProviderProfile profile,
                    List<RequestMessageItem> messages, String systemPrompt,
                    Listener listener) {
        try {
            Request request = provider.buildRequest(profile, messages, systemPrompt, true);
            sharedClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    LogUtils.e(TAG, "onFailure: " + ProviderRedaction.redact(e.getMessage()));
                    listener.onError("Network error: " + ProviderRedaction.redact(e.getMessage()));
                    listener.onComplete();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    LogUtils.e(TAG, "onResponse code: " + response.code());
                    if (response.isSuccessful()) {
                        if (isEventStream(response)) {
                            streamSuccessfulResponse(provider, response, listener);
                        } else {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            for (ProviderStreamEvent event : provider.parseResponseEvents(responseBody)) {
                                listener.onStreamEvent(event);
                                if (ProviderStreamEvent.TYPE_CONTENT_DELTA.equals(event.getType()) && event.getTextDelta() != null) {
                                    listener.onMessage(event.getTextDelta());
                                }
                            }
                            listener.onComplete();
                        }
                    } else {
                        String errorBody = "";
                        try {
                            errorBody = response.body() != null ? response.body().string() : "";
                        } catch (Exception ignored) {}
                        String errorMsg = ProviderRedaction.redact(provider.parseError(response.code(), errorBody));
                        listener.onError(errorMsg);
                        listener.onComplete();
                    }
                }
            });
        } catch (Exception e) {
            LogUtils.e(TAG, "Request error: " + ProviderRedaction.redact(e.getMessage()));
            listener.onError("Request error: " + ProviderRedaction.redact(e.getMessage()));
            listener.onComplete();
        }
    }

    private boolean isEventStream(Response response) {
        String contentType = response.header("Content-Type", "");
        return contentType != null && contentType.toLowerCase().contains("text/event-stream");
    }

    private void streamSuccessfulResponse(AIProvider provider, Response response, Listener listener) {
        try {
            if (response.body() == null) {
                listener.onComplete();
                return;
            }
            BufferedSource source = response.body().source();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (provider.isStreamComplete(line)) break;
                try {
                    List<ProviderStreamEvent> events = provider.parseStreamEvents(line);
                    if (events.isEmpty()) {
                        String content = provider.parseStreamChunk(line);
                        if (content != null && !content.isEmpty()) listener.onMessage(content);
                    } else {
                        for (ProviderStreamEvent event : events) {
                            listener.onStreamEvent(event);
                            if (ProviderStreamEvent.TYPE_CONTENT_DELTA.equals(event.getType())
                                && event.getTextDelta() != null) {
                                listener.onMessage(event.getTextDelta());
                            }
                        }
                    }
                } catch (AIProviderException e) {
                    LogUtils.e(TAG, "Stream parse error: " + ProviderRedaction.redact(e.getMessage()));
                }
            }
            listener.onComplete();
        } catch (Exception e) {
            LogUtils.e(TAG, "onResponse data error: " + ProviderRedaction.redact(e.getMessage()));
            listener.onError("Data error: " + ProviderRedaction.redact(e.getMessage()));
            listener.onComplete();
        }
    }
}
