package com.termux.zerocore.llm.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.example.xh_lib.utils.UUtils
import com.termux.R
import com.termux.zerocore.ai.llm.data.ChatDatabaseHelper
import com.termux.zerocore.ai.llm.model.Config
import com.termux.zerocore.ai.model.ProviderProfile
import com.termux.zerocore.ai.model.ProviderProfileContract
import com.termux.zerocore.ai.provider.ClaudeProvider
import com.termux.zerocore.ai.provider.GeminiProvider
import com.termux.zerocore.ai.provider.OpenAIProvider
import com.termux.zerocore.ftp.utils.UserSetManage
import com.termux.zerocore.settings.BaseTitleActivity

class ZeroTermuxLLMSettingsActivity : BaseTitleActivity() {
    companion object {
        val TAG: String = ZeroTermuxLLMSettingsActivity::class.java.simpleName
    }

    private data class ProfileOption(
        val title: String,
        val defaultName: String,
        val protocol: String,
        val endpointPolicy: String,
        val authMode: String,
        val defaultUrl: String,
        val defaultModel: String,
        val capabilitiesJson: String,
        val reasoningEnabled: Boolean = false,
        val reasoningEffort: String = ""
    )

    private val mKeyClick by lazy { findViewById<EditText>(R.id.key_click) }
    private val mKeyClickSummary by lazy { findViewById<TextView>(R.id.key_click_summary) }
    private val mAiVisibleSwitch by lazy { findViewById<SwitchCompat>(R.id.ai_visible_switch) }
    private val mAiVisibleLayout by lazy { findViewById<LinearLayout>(R.id.ai_visible_layout) }
    private val mProviderListContainer by lazy { findViewById<LinearLayout>(R.id.provider_list_container) }
    private val mAddProviderCard by lazy { findViewById<CardView>(R.id.add_provider_card) }
    private val mSystemPromptEdit by lazy { findViewById<EditText>(R.id.system_prompt_edit) }

    private lateinit var dbHelper: ChatDatabaseHelper

    private val profileOptions: List<ProfileOption> by lazy {
        listOf(
            ProfileOption(
                getString(R.string.ai_format_deepseek_preset),
                "DeepSeek",
                ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
                ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT,
                ProviderProfileContract.AUTH_BEARER_AUTHORIZATION,
                ProviderProfileContract.DEEPSEEK_BASE_URL,
                ProviderProfileContract.DEEPSEEK_DEFAULT_MODEL,
                capabilitiesJson(modelList = true, reasoning = true)
            ),
            ProfileOption(
                getString(R.string.ai_format_openai),
                "OpenAI Chat",
                ProviderProfileContract.PROTOCOL_OPENAI_CHAT,
                ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT,
                ProviderProfileContract.AUTH_BEARER_AUTHORIZATION,
                ProviderProfileContract.OPENAI_BASE_URL,
                ProviderProfileContract.defaultModelName(ProviderProfileContract.PROTOCOL_OPENAI_CHAT),
                capabilitiesJson(modelList = true, reasoning = true)
            ),
            ProfileOption(
                getString(R.string.ai_format_claude),
                "Claude",
                ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES,
                ProviderProfileContract.ENDPOINT_CLAUDE_V1_MESSAGES,
                ProviderProfileContract.AUTH_ANTHROPIC_X_API_KEY,
                ProviderProfileContract.CLAUDE_BASE_URL,
                ProviderProfileContract.defaultModelName(ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES),
                capabilitiesJson(modelList = false, reasoning = true)
            ),
            ProfileOption(
                getString(R.string.ai_format_gemini),
                "Gemini",
                ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT,
                ProviderProfileContract.ENDPOINT_GEMINI_V1BETA_GENERATE_CONTENT,
                ProviderProfileContract.AUTH_GOOGLE_X_GOOG_API_KEY,
                ProviderProfileContract.GEMINI_BASE_URL,
                ProviderProfileContract.defaultModelName(ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT),
                capabilitiesJson(modelList = false, reasoning = false)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zero_termux_llm_settings)
        setBaseTitle(UUtils.getString(R.string.ai_settings_title))
        dbHelper = ChatDatabaseHelper(this)
        initView()
        initStatus()
        initProviderList()
        initSystemPrompt()
    }

    private fun initView() {
        setSwitchStatus(mAiVisibleSwitch, mAiVisibleLayout)

        val commandLink = UserSetManage.get().getZTUserBean().commandLink
        if (commandLink.isNullOrEmpty()) {
            mKeyClick.setText(Config.COMMANDS)
        } else {
            mKeyClick.setText(commandLink)
        }

        mKeyClick.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val ztUserBean = UserSetManage.get().getZTUserBean()
                var command = s?.toString()
                if (!TextUtils.isEmpty(command) && command!!.contains("，")) {
                    command = command.replace("，", ",")
                    mKeyClick.setText(command)
                    mKeyClick.setSelection(command.length)
                }
                ztUserBean.commandLink = command
                UserSetManage.get().setZTUserBean(ztUserBean)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        mAddProviderCard.setOnClickListener { showProviderDialog(null) }
    }

    private fun initStatus() {
        val ztUserBean = UserSetManage.get().getZTUserBean()
        mAiVisibleSwitch.isChecked = ztUserBean.isIsCustomVisibleTerminal

        mKeyClickSummary.text = getKeyClickText(
            UUtils.getString(R.string.deepseek_settings_recognition_edit_keyword),
            UUtils.getString(R.string.deepseek_settings_recognition_edit_info),
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    mKeyClick.setText(Config.COMMANDS)
                }
            })
        mKeyClickSummary.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun initProviderList() {
        refreshProviderList()
    }

    private fun refreshProviderList() {
        mProviderListContainer.removeAllViews()
        val providers = dbHelper.getAllProviders()
        for (provider in providers) addProviderItemView(provider)
    }

    private fun addProviderItemView(provider: ProviderProfile) {
        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_provider_setting, mProviderListContainer, false) as CardView

        val nameText = itemView.findViewById<TextView>(R.id.provider_item_name)
        val formatText = itemView.findViewById<TextView>(R.id.provider_item_format)
        val endpointText = itemView.findViewById<TextView>(R.id.provider_item_endpoint)
        val defaultBtn = itemView.findViewById<TextView>(R.id.provider_item_default)
        val editBtn = itemView.findViewById<TextView>(R.id.provider_item_edit)
        val deleteBtn = itemView.findViewById<TextView>(R.id.provider_item_delete)

        nameText.text = if (provider.isDefault) {
            "${provider.name} (${getString(R.string.ai_provider_default_badge)})"
        } else {
            provider.name
        }
        formatText.text = getProfileDisplayName(provider)
        endpointText.text = endpointPreview(provider)

        if (provider.isDefault) {
            defaultBtn.visibility = View.GONE
        } else {
            defaultBtn.visibility = View.VISIBLE
            defaultBtn.setOnClickListener {
                dbHelper.setDefaultProvider(provider.id)
                Toast.makeText(this, R.string.ai_provider_set_default_done, Toast.LENGTH_SHORT).show()
                refreshProviderList()
            }
        }

        editBtn.setOnClickListener { showProviderDialog(provider) }
        deleteBtn.setOnClickListener {
            if (dbHelper.getAllProviders().size <= 1) {
                Toast.makeText(this, R.string.ai_provider_delete_last, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(String.format(getString(R.string.ai_provider_delete_confirm), provider.name))
                .setPositiveButton(R.string.ai_confirm) { _, _ ->
                    dbHelper.deleteProvider(provider.id)
                    Toast.makeText(this, R.string.ai_provider_deleted, Toast.LENGTH_SHORT).show()
                    refreshProviderList()
                }
                .setNegativeButton(R.string.ai_cancel, null)
                .show()
        }

        mProviderListContainer.addView(itemView)
    }

    private fun showProviderDialog(existing: ProviderProfile?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_provider_edit, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.provider_name)
        val formatSpinner = dialogView.findViewById<Spinner>(R.id.provider_format_spinner)
        val urlEdit = dialogView.findViewById<EditText>(R.id.provider_url)
        val keyEdit = dialogView.findViewById<EditText>(R.id.provider_key)
        val modelEdit = dialogView.findViewById<EditText>(R.id.provider_model)
        val endpointPreviewText = dialogView.findViewById<TextView>(R.id.provider_endpoint_preview)
        val modelsPreviewText = dialogView.findViewById<TextView>(R.id.provider_models_preview)
        val authSummaryText = dialogView.findViewById<TextView>(R.id.provider_auth_summary)
        val capabilitiesSummaryText = dialogView.findViewById<TextView>(R.id.provider_capabilities_summary)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileOptions.map { it.title })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatSpinner.adapter = adapter

        fun selectedOption(): ProfileOption {
            val position = formatSpinner.selectedItemPosition
            val index = if (position < 0) 0 else position.coerceAtMost(profileOptions.size - 1)
            return profileOptions[index]
        }

        fun refreshProfileHints(forceDefaults: Boolean) {
            val option = selectedOption()
            if (forceDefaults || nameEdit.text.toString().trim().isEmpty() || isDefaultName(nameEdit.text.toString())) {
                nameEdit.setText(option.defaultName)
            }
            if (forceDefaults || urlEdit.text.toString().trim().isEmpty() || isKnownDefaultOrEndpoint(urlEdit.text.toString())) {
                urlEdit.setText(option.defaultUrl)
            }
            if (forceDefaults || modelEdit.text.toString().trim().isEmpty() || isKnownDefaultModel(modelEdit.text.toString())) {
                modelEdit.setText(option.defaultModel)
            }
            authSummaryText.text = authSummary(option)
            capabilitiesSummaryText.text = capabilitiesSummary(option)
            endpointPreviewText.text = "${getString(R.string.ai_provider_endpoint_preview)}\uFF1A${endpointPreview(option, urlEdit.text.toString(), modelEdit.text.toString())}"
            modelsPreviewText.text = "${getString(R.string.ai_provider_models_preview)}\uFF1A${modelsEndpointPreview(option, urlEdit.text.toString(), modelEdit.text.toString())}"
        }

        val updatePreviewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                endpointPreviewText.text = "${getString(R.string.ai_provider_endpoint_preview)}\uFF1A${endpointPreview(selectedOption(), urlEdit.text.toString(), modelEdit.text.toString())}"
                modelsPreviewText.text = "${getString(R.string.ai_provider_models_preview)}\uFF1A${modelsEndpointPreview(selectedOption(), urlEdit.text.toString(), modelEdit.text.toString())}"
            }
        }
        urlEdit.addTextChangedListener(updatePreviewWatcher)
        modelEdit.addTextChangedListener(updatePreviewWatcher)

        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshProfileHints(forceDefaults = existing == null && !first)
                first = false
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (existing != null) {
            nameEdit.setText(existing.name)
            urlEdit.setText(normalizeBaseUrlForProfile(existing, existing.apiUrl))
            keyEdit.setText(existing.apiKey)
            modelEdit.setText(existing.modelName)
            val profileIndex = profileOptions.indexOfFirst {
                it.protocol == existing.protocol && it.endpointPolicy == existing.endpointPathPolicy
            }.let { if (it >= 0) it else profileOptions.indexOfFirst { opt -> opt.protocol == existing.protocol } }
            if (profileIndex >= 0) formatSpinner.setSelection(profileIndex)
            refreshProfileHints(forceDefaults = false)
        } else {
            formatSpinner.setSelection(0)
            refreshProfileHints(forceDefaults = true)
        }

        val title = if (existing != null) getString(R.string.ai_provider_edit) else getString(R.string.ai_provider_add)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.ai_confirm) { _, _ ->
                val option = selectedOption()
                val name = nameEdit.text.toString().trim()
                val rawUrl = urlEdit.text.toString().trim()
                val key = keyEdit.text.toString().trim()
                val model = modelEdit.text.toString().trim()
                val baseUrl = normalizeBaseUrl(option, rawUrl)

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (baseUrl.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_url_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (model.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_model_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (key.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_key_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val profile = existing ?: ProviderProfile()
                if (existing == null) profile.setId(0) else profile.setId(existing.id)
                profile.setName(name)
                profile.setProtocol(option.protocol)
                profile.setEndpointPathPolicy(option.endpointPolicy)
                profile.setAuthMode(option.authMode)
                profile.setApiUrl(baseUrl)
                profile.setApiKey(key)
                profile.setModelName(model)
                profile.setDefault(existing?.isDefault ?: false)
                profile.setCapabilitiesJson(option.capabilitiesJson)
                profile.setAdvancedParamsJson("{}")
                profile.setReasoningEnabled(existing?.isReasoningEnabled ?: option.reasoningEnabled)
                profile.setReasoningEffort(existing?.reasoningEffort ?: option.reasoningEffort)

                if (existing != null) dbHelper.updateProvider(profile) else dbHelper.insertProvider(profile)
                Toast.makeText(this, R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
                refreshProviderList()
            }
            .setNegativeButton(R.string.ai_cancel, null)
            .show()
    }

    private fun getProfileDisplayName(provider: ProviderProfile): String {
        return profileOptions.firstOrNull {
            it.protocol == provider.protocol && it.endpointPolicy == provider.endpointPathPolicy
        }?.title ?: when (provider.protocol) {
            ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES -> getString(R.string.ai_format_claude)
            ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT -> getString(R.string.ai_format_gemini)
            else -> getString(R.string.ai_format_openai)
        }
    }

    private fun endpointPreview(provider: ProviderProfile): String {
        if (provider.hasUnknownEndpointPathPolicy() || provider.hasUnknownAuthMode()) {
            return getString(R.string.ai_provider_unsupported_profile)
        }
        return try {
            when (provider.protocol) {
                ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES -> ClaudeProvider.buildMessagesUrl(provider)
                ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT -> GeminiProvider.buildGenerateContentUrl(provider, false)
                else -> OpenAIProvider.buildChatCompletionsUrl(provider)
            }
        } catch (e: IllegalArgumentException) {
            getString(R.string.ai_provider_unsupported_profile)
        }
    }

    private fun endpointPreview(option: ProfileOption, rawUrl: String, model: String): String {
        val preview = previewProfile(option, rawUrl, model)
        return endpointPreview(preview)
    }

    private fun modelsEndpointPreview(option: ProfileOption, rawUrl: String, model: String): String {
        val preview = previewProfile(option, rawUrl, model)
        return when (preview.protocol) {
            ProviderProfileContract.PROTOCOL_OPENAI_CHAT -> OpenAIProvider.buildModelsUrl(preview)
            else -> getString(R.string.ai_provider_model_list_manual)
        }
    }

    private fun previewProfile(option: ProfileOption, rawUrl: String, model: String): ProviderProfile {
        return ProviderProfile(
            0,
            option.defaultName,
            option.protocol,
            option.endpointPolicy,
            option.authMode,
            normalizeBaseUrl(option, rawUrl),
            "",
            model.ifEmpty { option.defaultModel },
            false,
            option.capabilitiesJson,
            "{}",
            option.reasoningEnabled,
            option.reasoningEffort
        )
    }

    private fun normalizeBaseUrl(option: ProfileOption, rawUrl: String): String {
        return when (option.protocol) {
            ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES -> ClaudeProvider.normalizeBaseUrl(rawUrl)
            ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT -> GeminiProvider.normalizeBaseUrl(rawUrl)
            else -> OpenAIProvider.normalizeApiBaseUrl(rawUrl, option.endpointPolicy)
        }
    }

    private fun normalizeBaseUrlForProfile(provider: ProviderProfile, rawUrl: String): String {
        return when (provider.protocol) {
            ProviderProfileContract.PROTOCOL_CLAUDE_MESSAGES -> ClaudeProvider.normalizeBaseUrl(rawUrl)
            ProviderProfileContract.PROTOCOL_GEMINI_GENERATE_CONTENT -> GeminiProvider.normalizeBaseUrl(rawUrl)
            else -> {
                val policy = if (provider.hasUnknownEndpointPathPolicy()) {
                    ProviderProfileContract.defaultEndpointPolicy(provider.protocol)
                } else {
                    provider.endpointPathPolicy
                }
                OpenAIProvider.normalizeApiBaseUrl(rawUrl, policy)
            }
        }
    }

    private fun authSummary(option: ProfileOption): String {
        return when (option.authMode) {
            ProviderProfileContract.AUTH_ANTHROPIC_X_API_KEY -> getString(R.string.ai_provider_auth_claude)
            ProviderProfileContract.AUTH_GOOGLE_X_GOOG_API_KEY -> getString(R.string.ai_provider_auth_gemini)
            else -> getString(R.string.ai_provider_auth_bearer)
        }
    }

    private fun capabilitiesSummary(option: ProfileOption): String {
        val modelList = if (option.capabilitiesJson.contains("\"model_list\":true")) "模型列表" else "模型列表需手动"
        val reasoning = if (option.capabilitiesJson.contains("\"thinking_reasoning\":true")) "思考/推理展示" else "文本/工具展示"
        return "能力：Chat、流式、$reasoning、工具调用：仅展示，不会自动执行、$modelList"
    }

    private fun capabilitiesJson(modelList: Boolean, reasoning: Boolean): String {
        return "{\"streaming\":true,\"thinking_reasoning\":$reasoning,\"system_prompt\":true," +
            "\"model_list\":$modelList,\"tools_function_calling\":true,\"advanced_sampling\":true}"
    }

    private fun isDefaultName(value: String): Boolean = profileOptions.any { it.defaultName == value }

    private fun isKnownDefaultOrEndpoint(value: String): Boolean {
        if (value.isBlank()) return true
        return profileOptions.any { option ->
            val normalized = normalizeBaseUrl(option, value)
            normalized == option.defaultUrl || value == option.defaultUrl || value.startsWith(option.defaultUrl + "/")
        }
    }

    private fun isKnownDefaultModel(value: String): Boolean {
        return value.isBlank() || profileOptions.any { it.defaultModel == value } ||
            value == ProviderProfileContract.DEEPSEEK_LEGACY_CHAT_MODEL ||
            value == ProviderProfileContract.DEEPSEEK_LEGACY_REASONER_MODEL
    }

    private fun initSystemPrompt() {
        val ztUserBean = UserSetManage.get().getZTUserBean()
        val customPrompt = ztUserBean.customSystemPrompt
        if (!customPrompt.isNullOrEmpty()) {
            mSystemPromptEdit.setText(customPrompt)
        } else {
            mSystemPromptEdit.setText(UUtils.getString(R.string.deepseek_zs))
        }

        mSystemPromptEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val bean = UserSetManage.get().getZTUserBean()
                bean.customSystemPrompt = s?.toString() ?: ""
                UserSetManage.get().setZTUserBean(bean)
            }
        })
    }

    private fun getKeyClickText(keyword: String, text: String, clickableSpan: ClickableSpan): SpannableString {
        val spannableString = SpannableString(text)
        val startIndex = text.indexOf(keyword)
        val endIndex = startIndex + keyword.length

        if (startIndex != -1) {
            spannableString.setSpan(
                clickableSpan,
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                ForegroundColorSpan(UUtils.getColor(R.color.color_48baf3)),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannableString
    }

    private fun setSwitchStatus(switchCompat: SwitchCompat, linearLayout: LinearLayout) {
        linearLayout.setOnClickListener { switchCompat.isChecked = !switchCompat.isChecked }
        switchCompat.setOnCheckedChangeListener { _, _ ->
            val ztUserBean = UserSetManage.get().getZTUserBean()
            if (switchCompat == mAiVisibleSwitch) {
                ztUserBean.isIsCustomVisibleTerminal = switchCompat.isChecked
            }
            UserSetManage.get().setZTUserBean(ztUserBean)
        }
    }
}
