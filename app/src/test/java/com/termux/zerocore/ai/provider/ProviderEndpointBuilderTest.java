package com.termux.zerocore.ai.provider;

import com.termux.zerocore.ai.model.ProviderProfile;
import com.termux.zerocore.ai.model.ProviderProfileContract;

import org.junit.Assert;
import org.junit.Test;

import okhttp3.Request;

public class ProviderEndpointBuilderTest {

    @Test
    public void deepSeekUsesOfficialNoV1ChatAndModelsEndpointsFromBaseUrl() {
        ProviderProfile profile = ProviderProfile.deepSeekDefault("provider-demo-abcdefghijklmnop");

        Assert.assertEquals("https://api.deepseek.com/chat/completions", OpenAIProvider.buildChatCompletionsUrl(profile));
        Assert.assertEquals("https://api.deepseek.com/models", OpenAIProvider.buildModelsUrl(profile));
    }

    @Test
    public void deepSeekFullEndpointInputNormalizesBackToBaseUrl() {
        ProviderProfile profile = ProviderProfile.deepSeekDefault("provider-demo-abcdefghijklmnop");
        profile.setApiUrl("https://api.deepseek.com/v1/chat/completions");

        Assert.assertEquals("https://api.deepseek.com/chat/completions", OpenAIProvider.buildChatCompletionsUrl(profile));
        Assert.assertEquals("https://api.deepseek.com/models", OpenAIProvider.buildModelsUrl(profile));
    }

    @Test
    public void openAiCompatibleUsesV1ChatAndModelsEndpoints() {
        ProviderProfile profile = new ProviderProfile(0, "OpenAI", ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
            ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT, ProviderProfileContract.AUTH_BEARER_AUTHORIZATION,
            "https://api.openai.com", "provider-demo-abcdefghijklmnop", "gpt-4o",
            false, "{}", "{}", false, "");

        Assert.assertEquals("https://api.openai.com/v1/chat/completions", OpenAIProvider.buildChatCompletionsUrl(profile));
        Assert.assertEquals("https://api.openai.com/v1/models", OpenAIProvider.buildModelsUrl(profile));
    }

    @Test
    public void claudeUsesMessagesEndpointAndHeaderAuth() {
        ProviderProfile profile = new ProviderProfile(0, "Claude", ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES,
            ProviderProfileContract.ENDPOINT_CLAUDE_V1_MESSAGES, ProviderProfileContract.AUTH_ANTHROPIC_X_API_KEY,
            "https://api.anthropic.com/v1/messages", "provider-demo-abcdefghijklmnop", "claude-sonnet-4-5",
            false, "{}", "{}", false, "");

        Assert.assertEquals("https://api.anthropic.com/v1/messages", ClaudeProvider.buildMessagesUrl(profile));
        Request request = new ClaudeProvider().buildRequest(profile, java.util.Collections.emptyList(), "", false);
        Assert.assertEquals("provider-demo-abcdefghijklmnop", request.header("x-api-key"));
        Assert.assertEquals("2023-06-01", request.header("anthropic-version"));
    }

    @Test
    public void geminiUsesGenerateContentAndStreamAltSseWithHeaderAuthByDefault() {
        ProviderProfile profile = new ProviderProfile(0, "Gemini", ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT,
            ProviderProfileContract.ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT, ProviderProfileContract.AUTH_GOOGLE_X_GOOG_API_KEY,
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent" + "?" + "key=old",
            "provider-demo-abcdefghijklmnop", "gemini-2.5-flash", false, "{}", "{}", false, "");

        Assert.assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            GeminiProvider.buildGenerateContentUrl(profile, false));
        Assert.assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            GeminiProvider.buildGenerateContentUrl(profile, true));

        Request request = new GeminiProvider().buildRequest(profile, java.util.Collections.emptyList(), "", false);
        Assert.assertEquals("provider-demo-abcdefghijklmnop", request.header("x-goog-api-key"));
        Assert.assertNull(request.url().queryParameter("key"));
    }

    @Test
    public void geminiQueryKeyIsCompatibilityOnly() {
        ProviderProfile profile = new ProviderProfile(0, "Gemini Query", ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT,
            ProviderProfileContract.ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT, ProviderProfileContract.AUTH_QUERY_KEY_COMPAT_ONLY,
            ProviderProfileContract.GEMINI_BASE_URL, "provider-demo-abcdefghijklmnop", "gemini-2.5-flash",
            false, "{}", "{}", false, "");

        Request request = new GeminiProvider().buildRequest(profile, java.util.Collections.emptyList(), "", true);
        Assert.assertNull(request.header("x-goog-api-key"));
        Assert.assertEquals("provider-demo-abcdefghijklmnop", request.url().queryParameter("key"));
        Assert.assertEquals("sse", request.url().queryParameter("alt"));
    }

    @Test
    public void unknownAuthOrEndpointPolicyFailsBeforeUnsafeRequest() {
        ProviderProfile badOpenAiAuth = new ProviderProfile(0, "Bad", ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
            ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT, "unknown_auth", "https://api.example.invalid",
            "provider-demo-abcdefghijklmnop", "model", false, "{}", "{}", false, "");
        Assert.assertThrows(IllegalArgumentException.class, () -> new OpenAIProvider().buildRequest(badOpenAiAuth, java.util.Collections.emptyList(), "", false));

        ProviderProfile badClaudeEndpoint = new ProviderProfile(0, "Bad", ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES,
            "unknown_policy", ProviderProfileContract.AUTH_ANTHROPIC_X_API_KEY, "https://api.anthropic.com",
            "provider-demo-abcdefghijklmnop", "model", false, "{}", "{}", false, "");
        Assert.assertThrows(IllegalArgumentException.class, () -> ClaudeProvider.buildMessagesUrl(badClaudeEndpoint));
    }
}
