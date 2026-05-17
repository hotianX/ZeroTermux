package com.termux.zerocore.ai.model;

import org.junit.Assert;
import org.junit.Test;

public class ProviderRedactionTest {

    @Test
    public void redactsBearerAndProviderPrefixedTokensWithoutSkPrefixRequirement() {
        String redacted = ProviderRedaction.redact("Authorization: Bearer provider-demo-abcdefghijklmnop");
        Assert.assertEquals("Authorization: Bearer <redacted-provider-key>", redacted);

        String arbitraryProviderKey = ProviderRedaction.redact("token=custom-demo-ABCDEFGHIJKLMNOP123456");
        Assert.assertEquals("token=<redacted-provider-key>", arbitraryProviderKey);
    }

    @Test
    public void redactsApiHeadersAndQuerySecrets() {
        Assert.assertEquals("x-api-key: <redacted-provider-key>",
            ProviderRedaction.redact("x-api-key: provider-demo-abcdefghijklmnop"));
        Assert.assertEquals("x-goog-api-key=<redacted-provider-key>",
            ProviderRedaction.redact("x-goog-api-key=provider-demo-abcdefghijklmnop"));
        Assert.assertEquals("https://example.invalid/path?" + "key=<redacted-provider-key>&safe=1",
            ProviderRedaction.redact("https://example.invalid/path?" + "key=provider-demo-abcdefghijklmnop&safe=1"));
        Assert.assertEquals("https://example.invalid/path?api_key=<redacted-provider-key>",
            ProviderRedaction.redact("https://example.invalid/path?api_key=provider-demo-abcdefghijklmnop"));
        Assert.assertEquals("https://example.invalid/path?access_token=<redacted-provider-key>",
            ProviderRedaction.redact("https://example.invalid/path?access_token=provider-demo-abcdefghijklmnop"));
    }

    @Test
    public void redactionIsIdempotent() {
        String once = ProviderRedaction.redact("Authorization: Bearer provider-demo-abcdefghijklmnop");
        String twice = ProviderRedaction.redact(once);
        Assert.assertEquals(once, twice);
    }
}
