package com.termux.zerocore.deepseek.model;


import android.util.Log;

import com.example.xh_lib.utils.LogUtils;
import com.example.xh_lib.utils.UUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.termux.R;
import com.termux.zerocore.ftp.utils.UserSetManage;

import okhttp3.*;

import okio.BufferedSource;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeepSeekClient {
    private static final String TAG = DeepSeekClient.class.getSimpleName();
    private boolean isStream = false;

    private static final OkHttpClient sharedClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public interface Lis {
        void error();

        void msg(String msg, boolean isError);

        void end();
    }

    public DeepSeekClient() {

    }

    // 向DeepSeek提问
    public void ask(List<RequestMessageItem> messageItemList, boolean stream, Lis lis) {
        try {
            isStream = stream;

            // 把用户提问添加到请求中
            String requestBody = new RequestBodyParameter("deepseek-chat",
                messageItemList, stream).toJson();
            RequestBody body = RequestBody.create(requestBody,
                MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .addHeader("Authorization", "Bearer " + UserSetManage.Companion.get().getZTUserBean().getDeepSeekApiKey())
                .post(body)
                .build();

            sharedClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    LogUtils.e(TAG, "onFailure call: " + call + " ,e: " + e);
                    e.printStackTrace();
                    lis.msg("```call: " + call + "\n\nException: " + e, true);
                    lis.end();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    LogUtils.e(TAG, "onResponse call: " + call + " ,response: " + response);
                    if (response.isSuccessful()) {
                        try {
                            BufferedSource source = response.body().source();
                            String line;
                            while ((line = source.readUtf8Line()) != null) {
                                String processChunk = processChunk(line);
                                if (processChunk != null && processChunk.length() > 0) {
                                    lis.msg(processChunk, false);
                                }
                            }
                            lis.end();

                        } catch (Exception e) {
                            LogUtils.e(TAG, "onResponse data error: " + e);
                            lis.msg("Data Error Exception: " + e, true);
                            lis.end();
                        }
                    } else {
                        lis.msg(UUtils.getString(R.string.deepseek_input_key_error_start_info) + "\n\n```" + response
                            + "```\n\n" + (response.code() == 401 ? UUtils.getString(R.string.deepseek_input_key_error_info) : ""), true);
                        lis.end();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            UUtils.getHandler().postDelayed(() -> {
                lis.msg(UUtils.getString(R.string.deepseek_input_key_error_start_info) + " \n\n```" + e + "```"
                    + "\n\n" + UUtils.getString(R.string.deepseek_input_key_error_info_1), true);
                lis.end();
            }, 100);
        }
    }


    // 获取DeepSeek返回的内容
    public String getMsg(String msg) {
        try {
            JsonObject jsonObject = JsonParser.parseString(msg).getAsJsonObject();
            JsonArray choices = jsonObject.getAsJsonArray("choices");
            if (!isStream) {
                return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            } else {
                return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("delta").get("content").getAsString();
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "getMsg error: " + e);
        }
        return msg;
    }


    // 解析DeepSeek流式数据
    private String processChunk(String chunk) {
        // 去除"data: "前缀
        String cleanChunk = chunk.replaceFirst("^data: ", "");
        // 如果cleanChunk为空或不以'{'开头，则可能是换行符或其他非JSON数据，跳过
        if (cleanChunk.isEmpty() || cleanChunk.charAt(0) != '{') {
            return null;
        }
        return cleanChunk;
    }

}
