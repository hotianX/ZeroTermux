package com.termux.zerocore.ai.provider;

import com.example.xh_lib.utils.UUtils;
import com.termux.R;
import com.termux.zerocore.ai.llm.utils.CodeStringUtils;
import com.termux.zerocore.ai.model.ProviderRedaction;

final class ProviderErrorUtils {
    private ProviderErrorUtils() {}

    static String formatError(int statusCode, String providerMessage) {
        if (providerMessage == null || providerMessage.isEmpty()) providerMessage = "HTTP " + statusCode;
        providerMessage = ProviderRedaction.redact(providerMessage);
        return UUtils.getString(R.string.deepseek_input_key_error_start_info)
            + "\n\n```" + providerMessage + "```\n\n"
            + CodeStringUtils.getCodeString(statusCode);
    }
}
