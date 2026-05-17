package com.termux.zerocore.ai.data;

/**
 * Single source of truth for ZeroTermux AI conversation storage.
 *
 * Version history:
 * v1/v2: legacy DeepSeek chat/session state.
 * v3: current LLM schema with ai_providers and coarse format_type.
 * v4: provider protocol/policy/auth/capability/parameter fields.
 * v5: structured message fields for role/content/reasoning/tool metadata.
 */
public final class AiDatabaseContract {
    public static final String DATABASE_NAME = "custom_chat.db";
    public static final int DATABASE_VERSION = 5;

    private AiDatabaseContract() {}
}
