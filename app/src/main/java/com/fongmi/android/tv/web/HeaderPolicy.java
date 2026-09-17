package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.google.gson.JsonElement;

import java.util.Map;

import okhttp3.Headers;

public class HeaderPolicy {

    public static Map<String, String> parse(JsonElement element) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        try {
            if (element != null && element.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    String value = entry.getValue() == null || entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString();
                    if (!TextUtils.isEmpty(entry.getKey())) headers.put(entry.getKey(), value);
                }
            }
        } catch (Throwable ignored) {
        }
        return headers;
    }

    public static Headers of(Map<String, String> headers) {
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.set(entry.getKey(), entry.getValue());
        return builder.build();
    }
}
