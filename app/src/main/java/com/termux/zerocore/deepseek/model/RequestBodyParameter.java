package com.termux.zerocore.deepseek.model;

import com.google.gson.Gson;

import java.util.List;

public class RequestBodyParameter {
    public String model = "deepseek-chat";
    public List<RequestMessageItem> messages = null;
    public boolean stream = false;

    public RequestBodyParameter() {
    }

    public RequestBodyParameter(String model, List<RequestMessageItem> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
