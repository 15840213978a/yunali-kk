package com.fongmi.android.tv.web;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HomeWebBridge {

    private static final int INLINE_LIMIT = 12000;
    private static final int CHUNK_SIZE = 60000;

    private final WebHomeActivity activity;
    private final WebView webView;
    private final Map<String, String> results;

    public HomeWebBridge(WebHomeActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.results = new ConcurrentHashMap<>();
    }

    @JavascriptInterface
    public void invoke(String requestId, String method, String payload) {
        Task.execute(() -> handle(requestId, method, WebCall.object(payload)));
    }

    @JavascriptInterface
    public String resourceUrl(String url, String options) {
        return url;
    }

    @JavascriptInterface
    public int resultLength(String id) {
        String result = results.get(id);
        return result == null ? 0 : result.length();
    }

    @JavascriptInterface
    public String resultChunk(String id, int start) {
        String result = results.get(id);
        if (result == null || start < 0 || start >= result.length()) return "";
        return result.substring(start, Math.min(start + CHUNK_SIZE, result.length()));
    }

    @JavascriptInterface
    public void clearResult(String id) {
        results.remove(id);
    }

    private void handle(String requestId, String method, JsonObject payload) {
        try {
            String result = switch (method) {
                case "net.request" -> WebCall.request(payload);
                case "net.resourceUrl" -> quote(Json.safeString(payload, "url"));
                case "player.playUrl" -> playUrl(payload);
                case "player.playVod" -> playVod(payload);
                case "player.preloadArtwork", "player.control", "player.status" -> "{}";
                case "app.search" -> search(payload);
                case "app.openVod" -> openVod();
                case "app.openLive" -> open(() -> LiveActivity.start(activity), "{}");
                case "app.openKeep" -> open(() -> KeepActivity.start(activity), "{}");
                case "app.openSetting" -> throw new IllegalArgumentException("app.openSetting not supported");
                case "app.history" -> App.gson().toJson(History.get());
                case "pan.play" -> playUrl(payload);
                case "pan.check" -> throw new IllegalArgumentException("pan.check not supported");
                case "cache.get" -> quote(Prefers.getString(cacheKey(payload)));
                case "cache.set" -> cacheSet(payload);
                case "cache.del" -> cacheDel(payload);
                case "device.info" -> device();
                case "site.info" -> site();
                case "config.info" -> config();
                case "ext.info" -> extInfo();
                case "ext.log" -> "{}";
                case "ext.toast" -> extToast(payload);
                case "ui.setToolbar", "ui.setChrome", "ui.restoreChrome" -> "{}";
                case "ui.getViewport" -> activity.getViewportJson();
                case "navigation.back" -> back();
                case "navigation.reload" -> reload();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            resolve(requestId, result);
        } catch (Throwable e) {
            reject(requestId, e.getMessage());
        }
    }

    private String playUrl(JsonObject payload) {
        String url = Json.safeString(payload, "url");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        if (TextUtils.isEmpty(title)) title = url;
        final String playTitle = title;
        final String playUrl = url;
        final String playPic = pic;
        App.post(() -> VideoActivity.start(activity, SiteApi.PUSH, playUrl, playTitle, playPic));
        return "{}";
    }

    private String playVod(JsonObject payload) {
        String siteKey = Json.safeString(payload, "siteKey");
        String vodId = Json.safeString(payload, "vodId");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        if (TextUtils.isEmpty(title)) title = vodId;
        final String playTitle = title;
        final String playSiteKey = siteKey;
        final String playVodId = vodId;
        final String playPic = pic;
        App.post(() -> VideoActivity.start(activity, playSiteKey, playVodId, playTitle, playPic));
        return "{}";
    }

    private String search(JsonObject payload) {
        String keyword = Json.safeString(payload, "keyword");
        if (TextUtils.isEmpty(keyword)) throw new IllegalArgumentException("keyword is empty");
        App.post(() -> SearchActivity.start(activity, keyword));
        return "{}";
    }

    private String openVod() {
        App.post(activity::finishNative);
        return "{}";
    }

    private String back() {
        App.post(activity::finishNative);
        return "{}";
    }

    private String reload() {
        App.post(activity::reload);
        return "{}";
    }

    private String open(Runnable runnable, String result) {
        App.post(runnable);
        return result;
    }

    private String extToast(JsonObject payload) {
        String message = Json.safeString(payload, "message");
        if (!TextUtils.isEmpty(message)) App.post(() -> Notify.show(message));
        return "{}";
    }

    private String cacheSet(JsonObject payload) {
        Prefers.put(cacheKey(payload), Json.safeString(payload, "value"));
        return "{}";
    }

    private String cacheDel(JsonObject payload) {
        Prefers.remove(cacheKey(payload));
        return "{}";
    }

    private String cacheKey(JsonObject payload) {
        String rule = Json.safeString(payload, "rule");
        String key = Json.safeString(payload, "key");
        return "cache_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }

    private String device() {
        JsonObject object = new JsonObject();
        object.addProperty("address", Server.get().getAddress());
        return object.toString();
    }

    private String site() {
        Site site = VodConfig.get().getHome();
        JsonObject object = new JsonObject();
        object.addProperty("key", site.getKey());
        object.addProperty("name", site.getName());
        object.addProperty("homePage", site.getHomePage());
        object.addProperty("type", site.getType() == null ? 0 : site.getType());
        return object.toString();
    }

    private String config() {
        JsonObject object = new JsonObject();
        object.addProperty("id", VodConfig.getCid());
        object.addProperty("url", VodConfig.getUrl());
        object.addProperty("desc", VodConfig.getDesc());
        return object.toString();
    }

    private String extInfo() {
        Site site = VodConfig.get().getHome();
        JsonObject object = new JsonObject();
        object.addProperty("siteKey", site.getKey());
        object.addProperty("siteName", site.getName());
        object.addProperty("homePage", site.getHomePage());
        object.addProperty("enabled", false);
        return object.toString();
    }

    private void resolve(String requestId, String data) {
        String payload = TextUtils.isEmpty(data) ? "null" : data;
        if (payload.length() > INLINE_LIMIT) {
            String resultId = requestId + "_" + System.nanoTime();
            results.put(resultId, payload);
            payload = "{\"__fmResultId\":" + quote(resultId) + "}";
        }
        eval("window.fongmiNative&&window.fongmiNative.resolve(" + quote(requestId) + "," + payload + ")");
    }

    private void reject(String requestId, String error) {
        eval("window.fongmiNative&&window.fongmiNative.reject(" + quote(requestId) + "," + quote(error) + ")");
    }

    private void eval(String script) {
        App.post(() -> webView.evaluateJavascript(script, null));
    }

    private static String quote(String text) {
        return App.gson().toJson(text == null ? "" : text);
    }
}
