package com.termux.zerocore.ai.model;

import java.util.regex.Pattern;

/** Redacts provider credentials from logs, errors, URL previews, and debug payloads. */
public final class ProviderRedaction {
    private static final String REDACTED = "<redacted-provider-key>";
    private static final String REDACTION_SENTINEL = "__ZT_REDACTED_PROVIDER_KEY__";
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s`&]+");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(Bearer\\s+)([A-Za-z][A-Za-z0-9_-]*-[A-Za-z0-9_-]{12,}|[A-Za-z0-9_-]{24,})");
    private static final Pattern API_HEADER = Pattern.compile("(?i)((?:x-api-key|x-goog-api-key)\\s*[:=]\\s*)[^\\s`&]+");
    private static final Pattern QUERY_KEY = Pattern.compile("(?i)([?&](?:key|api_key|access_token)=)[^&\\s`]+");
    private static final Pattern PREFIXED_PROVIDER_KEY = Pattern.compile("(?=\\b[A-Za-z][A-Za-z0-9_-]{20,}\\b)[A-Za-z][A-Za-z0-9_-]*-[A-Za-z0-9_-]{12,}");

    private ProviderRedaction() {}

    public static String redact(String input) {
        if (input == null) return null;
        String value = input.replace(REDACTED, REDACTION_SENTINEL);
        value = AUTH_HEADER.matcher(value).replaceAll("$1" + REDACTION_SENTINEL);
        value = BEARER_TOKEN.matcher(value).replaceAll("$1" + REDACTION_SENTINEL);
        value = API_HEADER.matcher(value).replaceAll("$1" + REDACTION_SENTINEL);
        value = QUERY_KEY.matcher(value).replaceAll("$1" + REDACTION_SENTINEL);
        value = PREFIXED_PROVIDER_KEY.matcher(value).replaceAll(REDACTION_SENTINEL);
        return value.replace(REDACTION_SENTINEL, REDACTED);
    }
}
