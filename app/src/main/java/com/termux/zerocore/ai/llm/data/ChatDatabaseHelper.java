package com.termux.zerocore.ai.llm.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.xh_lib.utils.LogUtils;
import com.example.xh_lib.utils.SaveData;
import com.google.gson.Gson;
import com.termux.zerocore.ai.data.AiDatabaseContract;
import com.termux.zerocore.ai.model.ProviderProfile;
import com.termux.zerocore.ai.model.ProviderProfileContract;
import com.termux.zerocore.bean.ZTUserBean;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = ChatDatabaseHelper.class.getSimpleName();

    private static final String TABLE_CHAT_SESSIONS = "custom_chat_sessions";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_SESSION_ID = "session_id";
    private static final String COLUMN_SESSION_NAME = "session_name";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_PROVIDER_ID = "provider_id";

    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_MESSAGE_ID = "_id";
    private static final String COLUMN_SESSION_REF_ID = "session_id";
    private static final String COLUMN_MESSAGE_TEXT = "message_text";
    private static final String COLUMN_IS_USER = "is_user";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_AVATAR_RES_ID = "avatar_res_id";
    private static final String COLUMN_ROLE = "role";
    private static final String COLUMN_CONTENT_TEXT = "content_text";
    private static final String COLUMN_REASONING_TEXT = "reasoning_text";
    private static final String COLUMN_TOOL_CALLS_JSON = "tool_calls_json";
    private static final String COLUMN_RAW_METADATA_JSON = "raw_metadata_json";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_FINISH_REASON = "finish_reason";

    private static final String TABLE_AI_PROVIDERS = "ai_providers";
    private static final String COLUMN_PROVIDER_NAME = "name";
    private static final String COLUMN_FORMAT_TYPE = "format_type";
    private static final String COLUMN_PROTOCOL = "protocol";
    private static final String COLUMN_ENDPOINT_PATH_POLICY = "endpoint_path_policy";
    private static final String COLUMN_AUTH_MODE = "auth_mode";
    private static final String COLUMN_API_URL = "api_url";
    private static final String COLUMN_API_KEY = "api_key";
    private static final String COLUMN_MODEL_NAME = "model_name";
    private static final String COLUMN_IS_DEFAULT = "is_default";
    private static final String COLUMN_CAPABILITIES_JSON = "capabilities_json";
    private static final String COLUMN_ADVANCED_PARAMS_JSON = "advanced_params_json";
    private static final String COLUMN_REASONING_ENABLED = "reasoning_enabled";
    private static final String COLUMN_REASONING_EFFORT = "reasoning_effort";

    public ChatDatabaseHelper(Context context) {
        super(context, AiDatabaseContract.DATABASE_NAME, null, AiDatabaseContract.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSessionTable(db);
        createMessageTable(db);
        createProviderTable(db);
        insertDefaultProvider(db, readApiKeyFromSharedPrefs());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            if (oldVersion < 2) upgradeToV2(db);
            if (oldVersion < 3) upgradeToV3(db);
            if (oldVersion < 4) upgradeToV4(db);
            if (oldVersion < 5) upgradeToV5(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Never crash installed users by opening AI storage through an older helper path.
        // Keep the newer schema and make sure all v5 columns/tables exist.
        onUpgrade(db, 1, AiDatabaseContract.DATABASE_VERSION);
    }

    private void createSessionTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CHAT_SESSIONS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_SESSION_ID + " TEXT,"
            + COLUMN_SESSION_NAME + " TEXT,"
            + COLUMN_CREATED_AT + " INTEGER,"
            + COLUMN_PROVIDER_ID + " INTEGER" + ");");
    }

    private void createMessageTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGES + "("
            + COLUMN_MESSAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_SESSION_REF_ID + " TEXT,"
            + COLUMN_MESSAGE_TEXT + " TEXT,"
            + COLUMN_IS_USER + " INTEGER,"
            + COLUMN_TIMESTAMP + " INTEGER,"
            + COLUMN_AVATAR_RES_ID + " INTEGER,"
            + COLUMN_ROLE + " TEXT,"
            + COLUMN_CONTENT_TEXT + " TEXT,"
            + COLUMN_REASONING_TEXT + " TEXT,"
            + COLUMN_TOOL_CALLS_JSON + " TEXT,"
            + COLUMN_RAW_METADATA_JSON + " TEXT,"
            + COLUMN_STATUS + " TEXT,"
            + COLUMN_FINISH_REASON + " TEXT" + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session ON " + TABLE_MESSAGES
            + "(" + COLUMN_SESSION_REF_ID + ", " + COLUMN_TIMESTAMP + ")");
    }

    private void createProviderTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_AI_PROVIDERS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_PROVIDER_NAME + " TEXT NOT NULL,"
            + COLUMN_FORMAT_TYPE + " TEXT NOT NULL DEFAULT 'openai',"
            + COLUMN_PROTOCOL + " TEXT NOT NULL DEFAULT 'openai_chat',"
            + COLUMN_ENDPOINT_PATH_POLICY + " TEXT NOT NULL DEFAULT 'openai_v1_chat',"
            + COLUMN_AUTH_MODE + " TEXT NOT NULL DEFAULT 'bearer_authorization',"
            + COLUMN_API_URL + " TEXT NOT NULL,"
            + COLUMN_API_KEY + " TEXT DEFAULT '',"
            + COLUMN_MODEL_NAME + " TEXT NOT NULL,"
            + COLUMN_IS_DEFAULT + " INTEGER DEFAULT 0,"
            + COLUMN_CAPABILITIES_JSON + " TEXT DEFAULT '',"
            + COLUMN_ADVANCED_PARAMS_JSON + " TEXT DEFAULT '',"
            + COLUMN_REASONING_ENABLED + " INTEGER DEFAULT 0,"
            + COLUMN_REASONING_EFFORT + " TEXT DEFAULT ''" + ");");
    }

    private void upgradeToV2(SQLiteDatabase db) {
        createSessionTable(db);
        createMessageTable(db);
    }

    private void upgradeToV3(SQLiteDatabase db) {
        createSessionTable(db);
        createMessageTable(db);
        createProviderTable(db);
        addColumnIfMissing(db, TABLE_CHAT_SESSIONS, COLUMN_PROVIDER_ID, "INTEGER");
        long defaultProviderId = ensureDefaultProvider(db, readApiKeyFromSharedPrefs());
        if (defaultProviderId > 0) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PROVIDER_ID, defaultProviderId);
            db.update(TABLE_CHAT_SESSIONS, values, COLUMN_PROVIDER_ID + " IS NULL", null);
        }
    }

    private void upgradeToV4(SQLiteDatabase db) {
        createProviderTable(db);
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_PROTOCOL, "TEXT NOT NULL DEFAULT 'openai_chat'");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_ENDPOINT_PATH_POLICY, "TEXT NOT NULL DEFAULT 'openai_v1_chat'");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_AUTH_MODE, "TEXT NOT NULL DEFAULT 'bearer_authorization'");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_CAPABILITIES_JSON, "TEXT DEFAULT ''");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_ADVANCED_PARAMS_JSON, "TEXT DEFAULT ''");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_REASONING_ENABLED, "INTEGER DEFAULT 0");
        addColumnIfMissing(db, TABLE_AI_PROVIDERS, COLUMN_REASONING_EFFORT, "TEXT DEFAULT ''");
        migrateProviderRowsToV4(db);
    }

    private void upgradeToV5(SQLiteDatabase db) {
        createMessageTable(db);
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_ROLE, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_CONTENT_TEXT, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_REASONING_TEXT, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_TOOL_CALLS_JSON, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_RAW_METADATA_JSON, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_STATUS, "TEXT");
        addColumnIfMissing(db, TABLE_MESSAGES, COLUMN_FINISH_REASON, "TEXT");
        db.execSQL("UPDATE " + TABLE_MESSAGES + " SET " + COLUMN_ROLE + " = CASE WHEN "
            + COLUMN_IS_USER + " = 1 THEN 'user' ELSE 'assistant' END WHERE " + COLUMN_ROLE + " IS NULL");
        db.execSQL("UPDATE " + TABLE_MESSAGES + " SET " + COLUMN_CONTENT_TEXT + " = "
            + COLUMN_MESSAGE_TEXT + " WHERE " + COLUMN_CONTENT_TEXT + " IS NULL");
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(SQLiteDatabase db, String table, String column) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (column.equalsIgnoreCase(name)) return true;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return false;
    }

    private void migrateProviderRowsToV4(SQLiteDatabase db) {
        Cursor cursor = db.query(TABLE_AI_PROVIDERS, null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String name = getString(cursor, COLUMN_PROVIDER_NAME);
                String format = getString(cursor, COLUMN_FORMAT_TYPE);
                String model = getString(cursor, COLUMN_MODEL_NAME);
                String apiUrl = getString(cursor, COLUMN_API_URL);
                String protocol = ProviderProfileContract.normalizeProtocol(getString(cursor, COLUMN_PROTOCOL));
                if (format != null && !format.isEmpty()) protocol = ProviderProfileContract.normalizeProtocol(format);
                String endpointPolicy = ProviderProfileContract.defaultEndpointPolicy(protocol);
                String authMode = ProviderProfileContract.defaultAuthMode(protocol);
                if (ProviderProfileContract.PROTOCOL_OPENAI_CHAT.equals(protocol) && looksLikeLegacyDeepSeekProfile(name, model, apiUrl)) {
                    endpointPolicy = ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT;
                    authMode = ProviderProfileContract.AUTH_BEARER_AUTHORIZATION;
                    if (model == null || model.trim().isEmpty()) {
                        model = ProviderProfileContract.DEEPSEEK_DEFAULT_MODEL;
                    } else {
                        model = model.trim();
                    }
                    if (apiUrl == null || apiUrl.trim().isEmpty()) {
                        apiUrl = ProviderProfileContract.DEEPSEEK_BASE_URL;
                    }
                }
                ContentValues values = new ContentValues();
                values.put(COLUMN_FORMAT_TYPE, ProviderProfileContract.legacyFormatForProtocol(protocol));
                values.put(COLUMN_PROTOCOL, protocol);
                values.put(COLUMN_ENDPOINT_PATH_POLICY, endpointPolicy);
                values.put(COLUMN_AUTH_MODE, authMode);
                values.put(COLUMN_MODEL_NAME, model);
                values.put(COLUMN_API_URL, apiUrl);
                values.put(COLUMN_CAPABILITIES_JSON, defaultCapabilitiesJson(protocol, endpointPolicy));
                db.update(TABLE_AI_PROVIDERS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            }
        } finally {
            cursor.close();
        }
    }

    private boolean looksLikeLegacyDeepSeekProfile(String name, String model, String apiUrl) {
        return containsIgnoreCase(name, "deepseek")
            || containsIgnoreCase(apiUrl, "deepseek")
            || ProviderProfileContract.DEEPSEEK_LEGACY_CHAT_MODEL.equals(model)
            || ProviderProfileContract.DEEPSEEK_LEGACY_REASONER_MODEL.equals(model);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase().contains(needle.toLowerCase());
    }

    private String getString(Cursor cursor, String column) {
        int idx = cursor.getColumnIndex(column);
        if (idx < 0 || cursor.isNull(idx)) return "";
        return cursor.getString(idx);
    }

    private String readApiKeyFromSharedPrefs() {
        try {
            String json = SaveData.INSTANCE.getStringOther("zero_termux_user_bean");
            if (json != null && !json.isEmpty() && !"def".equals(json)) {
                ZTUserBean bean = new Gson().fromJson(json, ZTUserBean.class);
                if (bean != null) {
                    String customKey = bean.getCustomApiKey();
                    if (customKey != null && !customKey.trim().isEmpty()) return customKey.trim();
                    String legacyDeepSeekKey = bean.getDeepSeekApiKey();
                    if (legacyDeepSeekKey != null && !legacyDeepSeekKey.trim().isEmpty()) return legacyDeepSeekKey.trim();
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to read API key from SharedPreferences: " + e);
        }
        return "";
    }

    private long insertDefaultProvider(SQLiteDatabase db, String apiKey) {
        ProviderProfile profile = ProviderProfile.deepSeekDefault(apiKey);
        return db.insert(TABLE_AI_PROVIDERS, null, valuesForProvider(profile));
    }

    private long ensureDefaultProvider(SQLiteDatabase db, String apiKey) {
        Cursor cursor = db.query(TABLE_AI_PROVIDERS, new String[]{COLUMN_ID}, COLUMN_IS_DEFAULT + " = 1", null, null, null, null);
        try {
            if (cursor.moveToFirst()) return cursor.getLong(0);
        } finally {
            cursor.close();
        }
        return insertDefaultProvider(db, apiKey);
    }

    private ContentValues valuesForProvider(ProviderProfile profile) {
        String protocol = profile.getProtocol();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROVIDER_NAME, profile.getName());
        values.put(COLUMN_FORMAT_TYPE, ProviderProfileContract.legacyFormatForProtocol(protocol));
        values.put(COLUMN_PROTOCOL, protocol);
        values.put(COLUMN_ENDPOINT_PATH_POLICY, profile.getEndpointPathPolicy());
        values.put(COLUMN_AUTH_MODE, profile.getAuthMode());
        values.put(COLUMN_API_URL, profile.getApiUrl());
        values.put(COLUMN_API_KEY, profile.getApiKey() != null ? profile.getApiKey().trim() : "");
        values.put(COLUMN_MODEL_NAME, profile.getModelName());
        values.put(COLUMN_IS_DEFAULT, profile.isDefault() ? 1 : 0);
        values.put(COLUMN_CAPABILITIES_JSON, profile.getCapabilitiesJson() != null ? profile.getCapabilitiesJson() : defaultCapabilitiesJson(protocol, profile.getEndpointPathPolicy()));
        values.put(COLUMN_ADVANCED_PARAMS_JSON, profile.getAdvancedParamsJson() != null ? profile.getAdvancedParamsJson() : "{}");
        values.put(COLUMN_REASONING_ENABLED, profile.isReasoningEnabled() ? 1 : 0);
        values.put(COLUMN_REASONING_EFFORT, profile.getReasoningEffort() != null ? profile.getReasoningEffort() : "");
        return values;
    }

    private String defaultCapabilitiesJson(String protocol, String endpointPolicy) {
        boolean modelList = ProviderProfileContract.PROTOCOL_OPENAI_CHAT.equals(protocol)
            && (ProviderProfileContract.ENDPOINT_DEEPSEEK_NO_V1_CHAT.equals(endpointPolicy)
                || ProviderProfileContract.ENDPOINT_OPENAI_V1_CHAT.equals(endpointPolicy));
        return "{\"streaming\":true,\"thinking_reasoning\":true,\"system_prompt\":true,"
            + "\"model_list\":" + modelList + ",\"tools_function_calling\":true,\"advanced_sampling\":true}";
    }

    public long insertProvider(ProviderProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.insert(TABLE_AI_PROVIDERS, null, valuesForProvider(profile));
    }

    public boolean updateProvider(ProviderProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.update(TABLE_AI_PROVIDERS, valuesForProvider(profile), COLUMN_ID + " = ?", new String[]{String.valueOf(profile.getId())});
        return rows > 0;
    }

    public boolean updateDefaultProviderApiKey(String apiKey) {
        ProviderProfile defaultProvider = getDefaultProvider();
        if (defaultProvider == null) return false;
        defaultProvider.setApiKey(apiKey != null ? apiKey.trim() : "");
        return updateProvider(defaultProvider);
    }

    public boolean deleteProvider(long providerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_AI_PROVIDERS, COLUMN_ID + " = ?", new String[]{String.valueOf(providerId)}) > 0;
    }

    public List<ProviderProfile> getAllProviders() {
        List<ProviderProfile> providers = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_AI_PROVIDERS, null, null, null, null, null, COLUMN_IS_DEFAULT + " DESC, " + COLUMN_PROVIDER_NAME + " ASC");
        try {
            while (cursor.moveToNext()) providers.add(cursorToProvider(cursor));
        } finally {
            cursor.close();
        }
        return providers;
    }

    public ProviderProfile getProviderById(long providerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_AI_PROVIDERS, null, COLUMN_ID + " = ?", new String[]{String.valueOf(providerId)}, null, null, null);
        try {
            return cursor.moveToFirst() ? cursorToProvider(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public ProviderProfile getDefaultProvider() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_AI_PROVIDERS, null, COLUMN_IS_DEFAULT + " = 1", null, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) return cursorToProvider(cursor);
        } finally {
            cursor.close();
        }
        List<ProviderProfile> all = getAllProviders();
        return all.isEmpty() ? null : all.get(0);
    }

    public int getProviderCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_AI_PROVIDERS, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public void setDefaultProvider(long providerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.put(COLUMN_IS_DEFAULT, 0);
            db.update(TABLE_AI_PROVIDERS, clear, null, null);
            ContentValues set = new ContentValues();
            set.put(COLUMN_IS_DEFAULT, 1);
            db.update(TABLE_AI_PROVIDERS, set, COLUMN_ID + " = ?", new String[]{String.valueOf(providerId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private ProviderProfile cursorToProvider(Cursor cursor) {
        String protocol = getString(cursor, COLUMN_PROTOCOL);
        if (protocol.isEmpty()) protocol = getString(cursor, COLUMN_FORMAT_TYPE);
        return new ProviderProfile(
            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            getString(cursor, COLUMN_PROVIDER_NAME),
            protocol,
            getString(cursor, COLUMN_ENDPOINT_PATH_POLICY),
            getString(cursor, COLUMN_AUTH_MODE),
            getString(cursor, COLUMN_API_URL),
            getString(cursor, COLUMN_API_KEY),
            getString(cursor, COLUMN_MODEL_NAME),
            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_DEFAULT)) == 1,
            getString(cursor, COLUMN_CAPABILITIES_JSON),
            getString(cursor, COLUMN_ADVANCED_PARAMS_JSON),
            cursor.getColumnIndex(COLUMN_REASONING_ENABLED) >= 0 && cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REASONING_ENABLED)) == 1,
            getString(cursor, COLUMN_REASONING_EFFORT)
        );
    }

    public void insertSession(String sessionId, String sessionName) {
        insertSession(sessionId, sessionName, 0);
    }

    public void insertSession(String sessionId, String sessionName, long providerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_ID, sessionId);
        values.put(COLUMN_SESSION_NAME, sessionName);
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        if (providerId > 0) values.put(COLUMN_PROVIDER_ID, providerId);
        db.insert(TABLE_CHAT_SESSIONS, null, values);
    }

    public boolean updateSession(String sessionId, String newSessionName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_NAME, newSessionName);
        return db.update(TABLE_CHAT_SESSIONS, values, COLUMN_SESSION_ID + " = ?", new String[]{sessionId}) > 0;
    }

    public boolean updateSessionProvider(String sessionId, long providerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROVIDER_ID, providerId);
        return db.update(TABLE_CHAT_SESSIONS, values, COLUMN_SESSION_ID + " = ?", new String[]{sessionId}) > 0;
    }

    public boolean deleteSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_MESSAGES, COLUMN_SESSION_REF_ID + " = ?", new String[]{sessionId});
            int sessionsDeleted = db.delete(TABLE_CHAT_SESSIONS, COLUMN_SESSION_ID + " = ?", new String[]{sessionId});
            db.setTransactionSuccessful();
            return sessionsDeleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public boolean deleteSessions(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < sessionIds.size(); i++) {
                placeholders.append("?");
                if (i < sessionIds.size() - 1) placeholders.append(",");
            }
            String[] args = sessionIds.toArray(new String[0]);
            db.delete(TABLE_MESSAGES, COLUMN_SESSION_REF_ID + " IN (" + placeholders + ")", args);
            int sessionsDeleted = db.delete(TABLE_CHAT_SESSIONS, COLUMN_SESSION_ID + " IN (" + placeholders + ")", args);
            db.setTransactionSuccessful();
            return sessionsDeleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public ChatSession getSessionById(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CHAT_SESSIONS, null, COLUMN_SESSION_ID + " = ?", new String[]{sessionId}, null, null, null);
        try {
            return cursor.moveToFirst() ? cursorToSession(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public boolean sessionExists(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CHAT_SESSIONS, new String[]{COLUMN_SESSION_ID}, COLUMN_SESSION_ID + " = ?", new String[]{sessionId}, null, null, null);
        try {
            return cursor.getCount() > 0;
        } finally {
            cursor.close();
        }
    }

    public List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CHAT_SESSIONS, null, null, null, null, null, COLUMN_CREATED_AT + " DESC");
        try {
            while (cursor.moveToNext()) sessions.add(cursorToSession(cursor));
        } finally {
            cursor.close();
        }
        return sessions;
    }

    private ChatSession cursorToSession(Cursor cursor) {
        String sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID));
        String sessionName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_NAME));
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
        long providerId = 0;
        int providerIdx = cursor.getColumnIndex(COLUMN_PROVIDER_ID);
        if (providerIdx >= 0 && !cursor.isNull(providerIdx)) providerId = cursor.getLong(providerIdx);
        return new ChatSession(sessionId, sessionName, createdAt, providerId);
    }

    public void insertMessage(String sessionId, String messageText, boolean isUser, long timestamp, int avatarResId) {
        insertMessage(sessionId, messageText, isUser, timestamp, avatarResId, "", "");
    }

    public void insertMessage(String sessionId, String messageText, boolean isUser, long timestamp,
                              int avatarResId, String reasoningText, String toolCallsJson) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_REF_ID, sessionId);
        values.put(COLUMN_MESSAGE_TEXT, messageText);
        values.put(COLUMN_IS_USER, isUser ? 1 : 0);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_AVATAR_RES_ID, avatarResId);
        values.put(COLUMN_ROLE, isUser ? "user" : "assistant");
        values.put(COLUMN_CONTENT_TEXT, messageText);
        values.put(COLUMN_REASONING_TEXT, reasoningText != null ? reasoningText : "");
        values.put(COLUMN_TOOL_CALLS_JSON, toolCallsJson != null ? toolCallsJson : "");
        values.put(COLUMN_STATUS, "complete");
        db.insert(TABLE_MESSAGES, null, values);
    }

    public List<ChatMessage> getMessagesForSession(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES, null, COLUMN_SESSION_REF_ID + "=?", new String[]{sessionId}, null, null, COLUMN_TIMESTAMP + " ASC");
        try {
            while (cursor.moveToNext()) {
                String messageText = getString(cursor, COLUMN_CONTENT_TEXT);
                if (messageText.isEmpty()) messageText = getString(cursor, COLUMN_MESSAGE_TEXT);
                String reasoningText = getString(cursor, COLUMN_REASONING_TEXT);
                String toolCallsJson = getString(cursor, COLUMN_TOOL_CALLS_JSON);
                boolean isUser = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_USER)) == 1;
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                int avatarResId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AVATAR_RES_ID));
                messages.add(new ChatMessage(messageText, isUser, timestamp, avatarResId, reasoningText, toolCallsJson));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    public int deleteMessagesForSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_MESSAGES, COLUMN_SESSION_REF_ID + " = ?", new String[]{sessionId});
    }
}
