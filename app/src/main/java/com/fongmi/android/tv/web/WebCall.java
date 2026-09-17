package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebCall {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).dns(OkHttp.dns()).proxySelector(OkHttp.selector()).proxyAuthenticator(OkHttp.authenticator()).build();

    public static String request(JsonObject payload) {
        Response response = null;
        String url = Json.safeString(payload, "url");
        String method = getMethod(Json.safeString(payload, "method"));
        try {
            int timeout = getTimeout(payload);
            Map<String, String> headers = HeaderPolicy.parse(payload.get("headers"));
            Request.Builder builder = new Request.Builder().url(url).headers(HeaderPolicy.of(headers));
            builder.method(method, getBody(method, Json.safeString(payload, "body"), headers));
            response = client(timeout).newCall(builder.build()).execute();
            return toJson(response, Json.safeString(payload, "responseType"));
        } catch (Throwable e) {
            return error(e);
        } finally {
            if (response != null) response.close();
        }
    }

    private static int getTimeout(JsonObject payload) {
        try {
            return Math.max(payload.get("timeout").getAsInt(), 1);
        } catch (Exception e) {
            return 30;
        }
    }

    private static OkHttpClient client(int timeout) {
        long millis = TimeUnit.SECONDS.toMillis(timeout);
        return CLIENT.newBuilder().connectTimeout(millis, TimeUnit.MILLISECONDS).readTimeout(millis, TimeUnit.MILLISECONDS).writeTimeout(millis, TimeUnit.MILLISECONDS).build();
    }

    private static String getMethod(String method) {
        return TextUtils.isEmpty(method) ? "GET" : method.toUpperCase();
    }

    private static RequestBody getBody(String method, String body, Map<String, String> headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String type = headers.get("Content-Type");
        MediaType mediaType = MediaType.parse(TextUtils.isEmpty(type) ? "text/plain; charset=utf-8" : type);
        return RequestBody.create(body == null ? "" : body, mediaType);
    }

    private static String toJson(Response response, String responseType) throws Exception {
        byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
        JsonObject object = new JsonObject();
        object.addProperty("ok", response.isSuccessful());
        object.addProperty("status", response.code());
        object.addProperty("url", response.request().url().toString());
        object.add("headers", headers(response.headers()));
        String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if ("base64".equals(responseType)) object.addProperty("body", Util.base64(bytes));
        else if ("json".equals(responseType)) object.add("body", Json.parse(body));
        else object.addProperty("body", body);
        return object.toString();
    }

    private static JsonObject headers(okhttp3.Headers headers) {
        JsonObject object = new JsonObject();
        for (String name : headers.names()) {
            if (headers.values(name).size() == 1) object.addProperty(name, headers.get(name));
            else object.add(name, App.gson().toJsonTree(headers.values(name)));
        }
        return object;
    }

    private static String error(Throwable e) {
        JsonObject object = new JsonObject();
        object.addProperty("ok", false);
        object.addProperty("status", 500);
        object.addProperty("body", "");
        object.addProperty("error", e.getMessage());
        object.add("headers", new JsonObject());
        return object.toString();
    }

    public static JsonObject object(String json) {
        JsonElement element = Json.parse(json);
        return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
