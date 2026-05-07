package com.termux.zerocore.llm.activity

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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.example.xh_lib.utils.UUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.R
import com.termux.zerocore.ai.model.ProviderProfile
import com.termux.zerocore.llm.data.ChatDatabaseHelper
import com.termux.zerocore.llm.model.Config
import com.termux.zerocore.ftp.utils.UserSetManage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZeroTermuxLLMSettingsActivity : AppCompatActivity() {
    companion object {
        public val TAG = ZeroTermuxLLMSettingsActivity::class.java.simpleName
    }

    private val mKeyClick by lazy { findViewById<EditText>(R.id.key_click) }
    private val mKeyClickSummary by lazy { findViewById<TextView>(R.id.key_click_summary) }

    private val mAiVisibleSwitch by lazy { findViewById<SwitchCompat>(R.id.ai_visible_switch) }
    private val mAiVisibleLayout by lazy { findViewById<LinearLayout>(R.id.ai_visible_layout) }

    private val mProviderListContainer by lazy { findViewById<LinearLayout>(R.id.provider_list_container) }
    private val mAddProviderCard by lazy { findViewById<CardView>(R.id.add_provider_card) }
    private val mSystemPromptEdit by lazy { findViewById<EditText>(R.id.system_prompt_edit) }

    private lateinit var dbHelper: ChatDatabaseHelper

    // Format type display names and values
    private val formatTypes = arrayOf("deepseek", "openai", "claude", "gemini")
    private val reasoningEffortValues = arrayOf("high", "max")
    private val defaultUrls = mapOf(
        "deepseek" to "https://api.deepseek.com/chat/completions",
        "openai" to "https://api.openai.com/v1/chat/completions",
        "claude" to "https://api.anthropic.com/v1/messages",
        "gemini" to "https://generativelanguage.googleapis.com/v1beta"
    )
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zero_termux_llm_settings)
        dbHelper = ChatDatabaseHelper(this)
        initView()
        initStatus()
        initProviderList()
        initSystemPrompt()
    }

    private fun initView() {
         setSwitchStatus(mAiVisibleSwitch, mAiVisibleLayout)

        // 设置AI蓝色点击识别
        val commandLink = UserSetManage.get().getZTUserBean().commandLink
        if (commandLink.isNullOrEmpty()) {
            mKeyClick.setText(Config.COMMANDS)
        } else {
            mKeyClick.setText(commandLink)
        }

        mKeyClick.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int
            ) {

            }

            override fun onTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int
            ) {
                val ztUserBean = UserSetManage.get().getZTUserBean()
                var command = p0?.toString()
                if (!TextUtils.isEmpty(command) && command!!.contains("，")) {
                    command = p0?.toString()?.replace("，", ",")
                    mKeyClick.setText(command)
                    mKeyClick.setSelection(command!!.length);
                }
                ztUserBean.commandLink = command
                UserSetManage.get().setZTUserBean(ztUserBean)
            }

            override fun afterTextChanged(p0: Editable?) {

            }

        })

        // Add Provider button
        mAddProviderCard.setOnClickListener {
            showProviderDialog(null)
        }
    }

    private fun initStatus() {
        val ztUserBean = UserSetManage.get().getZTUserBean()
        mAiVisibleSwitch.isChecked = ztUserBean.isIsLlmVisibleTerminal

        mKeyClickSummary.text = getKeyClickText(UUtils.getString(R.string.llm_settings_recognition_edit_keyword),
            UUtils.getString(R.string.llm_settings_recognition_edit_info), object : ClickableSpan() {
            override fun onClick(widget: View) {
                mKeyClick.setText(Config.COMMANDS)
            }
        })
        mKeyClickSummary.movementMethod = LinkMovementMethod.getInstance()
    }

    // ========================= Provider Management =========================

    private fun initProviderList() {
        refreshProviderList()
    }

    private fun refreshProviderList() {
        mProviderListContainer.removeAllViews()
        val providers = dbHelper.getAllProviders()
        for (provider in providers) {
            addProviderItemView(provider)
        }
    }

    private fun addProviderItemView(provider: ProviderProfile) {
        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_provider_setting, mProviderListContainer, false) as CardView

        val nameText = itemView.findViewById<TextView>(R.id.provider_item_name)
        val formatText = itemView.findViewById<TextView>(R.id.provider_item_format)
        val defaultBtn = itemView.findViewById<TextView>(R.id.provider_item_default)
        val editBtn = itemView.findViewById<TextView>(R.id.provider_item_edit)
        val deleteBtn = itemView.findViewById<TextView>(R.id.provider_item_delete)

        nameText.text = if (provider.isDefault) {
            "${provider.name} (${getString(R.string.ai_provider_default_badge)})"
        } else {
            provider.name
        }

        formatText.text = getFormatDisplayName(provider.formatType)

        // Hide "Set Default" button if already default
        if (provider.isDefault) {
            defaultBtn.visibility = View.GONE
        } else {
            defaultBtn.setOnClickListener {
                dbHelper.setDefaultProvider(provider.id)
                Toast.makeText(this, R.string.ai_provider_set_default_done, Toast.LENGTH_SHORT).show()
                refreshProviderList()
            }
        }

        editBtn.setOnClickListener {
            showProviderDialog(provider)
        }

        deleteBtn.setOnClickListener {
            if (dbHelper.getProviderCount() <= 1) {
                Toast.makeText(this, R.string.ai_provider_delete_last, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.ai_provider_delete_confirm), provider.name))
                .setPositiveButton(R.string.ai_delete) { _, _ ->
                    if (dbHelper.deleteProviderAndReassignSessions(provider.id)) {
                        Toast.makeText(this, R.string.ai_provider_deleted, Toast.LENGTH_SHORT).show()
                        refreshProviderList()
                    } else {
                        Toast.makeText(this, R.string.ai_provider_delete_failed, Toast.LENGTH_SHORT).show()
                    }
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
        val refreshModels = dialogView.findViewById<TextView>(R.id.provider_refresh_models)
        val deepSeekOptions = dialogView.findViewById<LinearLayout>(R.id.deepseek_options_container)
        val deepSeekThinkingSwitch = dialogView.findViewById<SwitchCompat>(R.id.deepseek_thinking_switch)
        val deepSeekReasoningEffortSpinner = dialogView.findViewById<Spinner>(R.id.deepseek_reasoning_effort_spinner)

        // Format spinner setup
        val formatNames = arrayOf(
            getString(R.string.ai_format_deepseek),
            getString(R.string.ai_format_openai),
            getString(R.string.ai_format_claude),
            getString(R.string.ai_format_gemini)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formatNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatSpinner.adapter = adapter
        val effortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reasoningEffortValues)
        effortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deepSeekReasoningEffortSpinner.adapter = effortAdapter

        // Auto-fill URL when format changes
        formatSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFormat = formatTypes[position]
                deepSeekOptions.visibility = if (selectedFormat == "deepseek") View.VISIBLE else View.GONE
                refreshModels.visibility = if (selectedFormat == "deepseek") View.VISIBLE else View.GONE
                // Only auto-fill if URL is empty or matches a default URL
                val currentUrl = urlEdit.text.toString().trim()
                if (currentUrl.isEmpty() || defaultUrls.values.contains(currentUrl)) {
                    urlEdit.setText(defaultUrls[selectedFormat] ?: "")
                }
                if (selectedFormat == "deepseek" && modelEdit.text.toString().trim().isEmpty()) {
                    modelEdit.setText("deepseek-v4-pro")
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        refreshModels.setOnClickListener {
            refreshDeepSeekModels(urlEdit.text.toString().trim(), keyEdit.text.toString().trim(), modelEdit)
        }

        // Fill existing values if editing
        if (existing != null) {
            nameEdit.setText(existing.name)
            urlEdit.setText(existing.apiUrl)
            keyEdit.setText(existing.apiKey)
            modelEdit.setText(existing.modelName)
            val formatIndex = formatTypes.indexOf(existing.formatType)
            if (formatIndex >= 0) formatSpinner.setSelection(formatIndex)
            deepSeekThinkingSwitch.isChecked = existing.isDeepSeekThinkingEnabled()
            val effortIndex = reasoningEffortValues.indexOf(existing.getDeepSeekReasoningEffort())
            deepSeekReasoningEffortSpinner.setSelection(if (effortIndex >= 0) effortIndex else 0)
        } else {
            deepSeekThinkingSwitch.isChecked = true
            deepSeekReasoningEffortSpinner.setSelection(0)
        }

        val title = if (existing != null) getString(R.string.ai_provider_edit) else getString(R.string.ai_provider_add)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.ai_confirm) { _, _ ->
                val name = nameEdit.text.toString().trim()
                val url = urlEdit.text.toString().trim()
                val key = keyEdit.text.toString().trim()
                val model = modelEdit.text.toString().trim()
                val formatType = formatTypes[formatSpinner.selectedItemPosition]
                val optionsJson = buildOptionsJson(
                    formatType,
                    deepSeekThinkingSwitch.isChecked,
                    reasoningEffortValues[deepSeekReasoningEffortSpinner.selectedItemPosition]
                )

                // Validation
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (url.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_url_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (model.isEmpty()) {
                    Toast.makeText(this, R.string.ai_provider_model_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (existing != null) {
                    existing.name = name
                    existing.formatType = formatType
                    existing.apiUrl = url
                    existing.apiKey = key
                    existing.modelName = model
                    existing.optionsJson = optionsJson
                    dbHelper.updateProvider(existing)
                } else {
                    val profile = ProviderProfile(0, name, formatType, url, key, model, false, optionsJson)
                    dbHelper.insertProvider(profile)
                }

                Toast.makeText(this, R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
                refreshProviderList()
            }
            .setNegativeButton(R.string.ai_cancel, null)
            .show()
    }

    private fun getFormatDisplayName(formatType: String): String {
        return when (formatType) {
            "deepseek" -> getString(R.string.ai_format_deepseek)
            "claude" -> getString(R.string.ai_format_claude)
            "gemini" -> getString(R.string.ai_format_gemini)
            else -> getString(R.string.ai_format_openai)
        }
    }

    private fun buildOptionsJson(formatType: String, thinkingEnabled: Boolean, reasoningEffort: String): String {
        if (formatType != "deepseek") {
            return ""
        }
        val options = JsonObject()
        options.addProperty("thinking_enabled", thinkingEnabled)
        options.addProperty("reasoning_effort", reasoningEffort)
        return options.toString()
    }

    private fun refreshDeepSeekModels(chatUrl: String, apiKey: String, modelEdit: EditText) {
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.ai_provider_key_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val modelsUrl = getDeepSeekModelsUrl(chatUrl)
        val request = Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ZeroTermuxLLMSettingsActivity, R.string.ai_provider_models_failed, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val models = parseModelIds(body)
                runOnUiThread {
                    if (!response.isSuccessful || models.isEmpty()) {
                        val msg = if (response.isSuccessful) R.string.ai_provider_models_empty else R.string.ai_provider_models_failed
                        Toast.makeText(this@ZeroTermuxLLMSettingsActivity, msg, Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val currentModel = modelEdit.text.toString().trim()
                    val selectedModel = when {
                        currentModel.isNotEmpty() && models.contains(currentModel) -> currentModel
                        models.contains("deepseek-v4-pro") -> "deepseek-v4-pro"
                        else -> models.first()
                    }
                    modelEdit.setText(selectedModel)
                    Toast.makeText(
                        this@ZeroTermuxLLMSettingsActivity,
                        String.format(getString(R.string.ai_provider_models_loaded), models.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun getDeepSeekModelsUrl(chatUrl: String): String {
        val normalized = if (chatUrl.isEmpty()) defaultUrls["deepseek"]!! else chatUrl
        val url = normalized.trimEnd('/')
        return when {
            url.endsWith("/models") -> url
            url.endsWith("/chat/completions") -> url.removeSuffix("/chat/completions") + "/models"
            url.endsWith("/beta/completions") -> url.removeSuffix("/beta/completions") + "/models"
            else -> "$url/models"
        }
    }

    private fun parseModelIds(body: String): List<String> {
        return try {
            val data = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
            data.mapNotNull { item ->
                val obj = item.asJsonObject
                if (obj.has("id")) obj.get("id").asString else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========================= System Prompt =========================

    private fun initSystemPrompt() {
        val ztUserBean = UserSetManage.get().getZTUserBean()
        val customPrompt = ztUserBean.customSystemPrompt
        if (!customPrompt.isNullOrEmpty()) {
            mSystemPromptEdit.setText(customPrompt)
        } else {
            mSystemPromptEdit.setText(UUtils.getString(R.string.llm_zs))
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

    // ========================= Utils =========================

    private fun getKeyClickText(keyword: String, text: String, clickableSpan: ClickableSpan) :SpannableString {
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

             spannableString.setSpan(ForegroundColorSpan(UUtils.getColor(R.color.color_48baf3)), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return spannableString;
        }
        return spannableString;
    }

    private fun setSwitchStatus(switchCompat: SwitchCompat, linearLayout: LinearLayout) {
        linearLayout.setOnClickListener {
            switchCompat.isChecked = !(switchCompat.isChecked)
        }
        switchCompat.setOnCheckedChangeListener { buttonView, isChecked ->
            val ztUserBean = UserSetManage.get().getZTUserBean()
            when (switchCompat) {
                mAiVisibleSwitch -> {
                     ztUserBean.isIsLlmVisibleTerminal = switchCompat.isChecked
                 }

            }
            UserSetManage.get().setZTUserBean(ztUserBean)
        }
    }

}
