package com.termux.zerocore.ai.model;

import org.junit.Assert;
import org.junit.Test;

public class ProviderProfileContractTest {

    @Test
    public void deepSeekDefaultIsOpenAiCompatibleNoV1Preset() {
        ProviderProfile profile = ProviderProfile.deepSeekDefault("provider-demo-abcdefghijklmnop");

        Assert.assertEquals(ProviderProfileContract.PROTOCOL_OPENAI_CHAT, profile.getProtocol());
        Assert.assertEquals(ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT, profile.getEndpointPathPolicy());
        Assert.assertEquals(ProviderProfileContract.AUTH_BEARER_AUTHORIZATION, profile.getAuthMode());
        Assert.assertEquals(ProviderProfileContract.DEEPSEEK_BASE_URL, profile.getApiUrl());
        Assert.assertEquals(ProviderProfileContract.DEEPSEEK_DEFAULT_MODEL, profile.getModelName());
        Assert.assertEquals("provider-demo-abcdefghijklmnop", profile.getApiKey());
        Assert.assertFalse(profile.hasUnknownEndpointPathPolicy());
        Assert.assertFalse(profile.hasUnknownAuthMode());
    }

    @Test
    public void protocolAndLegacyFormatNormalizationAreStableStrings() {
        Assert.assertEquals(ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
            ProviderProfileContract.normalizeProtocol(ProviderProfileContract.LEGACY_FORMAT_OPENAI));
        Assert.assertEquals(ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES,
            ProviderProfileContract.normalizeProtocol(ProviderProfileContract.LEGACY_FORMAT_CLAUDE));
        Assert.assertEquals(ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT,
            ProviderProfileContract.normalizeProtocol(ProviderProfileContract.LEGACY_FORMAT_GEMINI));

        Assert.assertEquals(ProviderProfileContract.LEGACY_FORMAT_OPENAI,
            ProviderProfileContract.legacyFormatForProtocol(ProviderProfileContract.PROTOCOL_OPENAI_CHAT));
        Assert.assertEquals(ProviderProfileContract.LEGACY_FORMAT_CLAUDE,
            ProviderProfileContract.legacyFormatForProtocol(ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES));
        Assert.assertEquals(ProviderProfileContract.LEGACY_FORMAT_GEMINI,
            ProviderProfileContract.legacyFormatForProtocol(ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT));
    }

    @Test
    public void unknownEndpointAndAuthSourcesAreFlaggedWithoutCrashingProfile() {
        ProviderProfile profile = new ProviderProfile(0, "Unknown", ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
            "unknown_policy", "unknown_auth", "https://example.invalid", "provider-demo-abcdefghijklmnop",
            "model", false, "{}", "{}", false, "");

        Assert.assertEquals("unknown_policy", profile.getEndpointPathPolicy());
        Assert.assertEquals("unknown_auth", profile.getAuthMode());
        Assert.assertTrue(profile.hasUnknownEndpointPathPolicy());
        Assert.assertTrue(profile.hasUnknownAuthMode());
    }

    @Test
    public void arbitraryProviderKeyFormatsArePreservedWithoutSkPrefixValidation() {
        ProviderProfile profile = ProviderProfile.deepSeekDefault("provider-demo-ABCDEFGHIJKLMNOP123456");
        Assert.assertEquals("provider-demo-ABCDEFGHIJKLMNOP123456", profile.getApiKey());

        profile.setApiKey("custom_format_without_required_prefix_123456789");
        Assert.assertEquals("custom_format_without_required_prefix_123456789", profile.getApiKey());
    }
}
