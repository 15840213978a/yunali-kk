package com.fongmi.android.tv.web;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityWebHomeBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WebHomeActivity extends AppCompatActivity {

    private static final String BRIDGE = "fongmiBridge";
    private static final long DIAG_DELAY_MS = 4000;
    private static boolean active;

    private ActivityWebHomeBinding mBinding;
    private String home;
    private boolean errorShown;

    public static boolean isActive() {
        return active;
    }

    public static void start(android.app.Activity activity) {
        activity.startActivity(new Intent(activity, WebHomeActivity.class));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        active = true;
        mBinding = ActivityWebHomeBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mBinding.webView.canGoBack()) mBinding.webView.goBack();
                else finish();
            }
        });
        initWebView();
        loadHome();
    }

    private void initWebView() {
        WebView webView = mBinding.webView;
        Server.get().start();
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) settings.setOffscreenPreRaster(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setBackgroundColor(Color.BLACK);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new HomeWebBridge(this, webView), BRIDGE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                errorShown = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSdk();
                scheduleDiagnosis();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (!request.isForMainFrame() || errorShown) return;
                errorShown = true;
                showErrorPage(description(error.getDescription()), String.valueOf(error.getErrorCode()));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.e("WebHome", "console " + message.messageLevel() + ": " + message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
                return super.onConsoleMessage(message);
            }
        });
    }

    private String description(CharSequence text) {
        return text == null ? "" : text.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void scheduleDiagnosis() {
        mBinding.webView.postDelayed(() -> {
            if (errorShown || isFinishing() || isDestroyed()) return;
            mBinding.webView.evaluateJavascript("(function(){try{return JSON.stringify({text:(document.body?document.body.innerText:'').replace(/\\s+/g,' ').trim(),title:document.title||''});}catch(e){return '{}';}})()", value -> {
                String text = parseJsonField(value, "text");
                if (!TextUtils.isEmpty(text)) return;
                String title = parseJsonField(value, "title");
                if (!TextUtils.isEmpty(title)) return;
                showDiagnosisPage();
            });
        }, DIAG_DELAY_MS);
    }

    private String parseJsonField(String json, String key) {
        if (TextUtils.isEmpty(json) || "null".equals(json)) return "";
        try {
            return Json.safeString(Json.parse(json).isJsonObject() ? Json.parse(json).getAsJsonObject() : new JsonObject(), key);
        } catch (Throwable e) {
            return "";
        }
    }

    private void showErrorPage(String description, String code) {
        String body = "<h2>页面加载失败</h2><p><b>错误：</b>" + description + "（" + code + "）</p>";
        showPage(body);
    }

    private void showDiagnosisPage() {
        String body = "<h2>页面内容为空</h2><p>页面已打开但没有渲染出内容。常见原因是系统 WebView 版本过低，无法运行该页面使用的 JS 框架。</p><p>建议到应用商店更新「Android System WebView」和「Chrome」后重试。</p>";
        showPage(body);
    }

    private void showPage(String body) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>body{background:#141414;color:#e8e8e8;font-family:sans-serif;padding:28px;font-size:16px;line-height:1.7}h2{font-size:19px;margin:0 0 14px;color:#fff}a{color:#8ab4f8}</style></head><body>"
                + body
                + "<p><b>WebView：</b>" + description(webviewVersion()) + "<br><b>Android：</b>" + Build.VERSION.SDK_INT + "<br><b>地址：</b>" + description(home) + "</p>"
                + "<p><a href=\"" + home + "\">重新加载</a></p></body></html>";
        mBinding.webView.loadDataWithBaseURL(home, html, "text/html", "utf-8", null);
    }

    private String webviewVersion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null) return info.packageName + " " + info.versionName;
            }
            return "未知";
        } catch (Throwable e) {
            return "未知";
        }
    }

    private void loadHome() {
        Site site = VodConfig.get().getHome();
        if (site.isEmpty() || !site.hasHomePage()) {
            finish();
            return;
        }
        String url = site.getHomePage();
        if (UrlUtil.scheme(url).isEmpty()) url = UrlUtil.resolve(VodConfig.getUrl(), url);
        home = UrlUtil.convert(url);
        Map<String, String> headers = site.getHeader();
        String userAgent = header(headers, "User-Agent");
        if (!TextUtils.isEmpty(userAgent)) mBinding.webView.getSettings().setUserAgentString(userAgent);
        String cookie = header(headers, "Cookie");
        if (!TextUtils.isEmpty(cookie)) CookieManager.getInstance().setCookie(home, cookie);
        Map<String, String> requestHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(key) || value == null) continue;
            if ("User-Agent".equalsIgnoreCase(key) || "Cookie".equalsIgnoreCase(key)) continue;
            requestHeaders.put(key, value);
        }
        if (requestHeaders.isEmpty()) mBinding.webView.loadUrl(home);
        else mBinding.webView.loadUrl(home, requestHeaders);
    }

    private String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return TextUtils.isEmpty(entry.getValue()) ? "" : entry.getValue();
        }
        return "";
    }

    private void injectSdk() {
        mBinding.webView.evaluateJavascript(getSdk(), null);
    }

    private String getSdk() {
        return String.format(Locale.ROOT, """
                (function(){
                  if(window.fm&&window.fongmi){window.dispatchEvent(new CustomEvent('fmsdk'));return;}
                  if(document&&document.documentElement)document.documentElement.classList.add('fm-native');
                  window.fongmiClient={mode:'%s',isLeanback:%s};
                  const callbacks={};
                  let seq=0;
                  function invoke(method,payload){
                    return new Promise((resolve,reject)=>{
                      const id='fm_'+Date.now()+'_'+(++seq);
                      callbacks[id]={resolve,reject};
                      fongmiBridge.invoke(id,method,JSON.stringify(payload||{}));
                    });
                  }
                  function hydrate(data){
                    if(!data||!data.__fmResultId)return data;
                    const resultId=data.__fmResultId;
                    const length=fongmiBridge.resultLength(resultId);
                    let text='';
                    for(let start=0;start<length;start+=60000)text+=fongmiBridge.resultChunk(resultId,start);
                    fongmiBridge.clearResult(resultId);
                    return JSON.parse(text);
                  }
                  window.fongmiNative={
                    resolve:(id,data)=>{ if(callbacks[id]){ callbacks[id].resolve(hydrate(data)); delete callbacks[id]; } },
                    reject:(id,error)=>{ if(callbacks[id]){ callbacks[id].reject(new Error(error||'')); delete callbacks[id]; } }
                  };
                  if(!window.__fmUrlHook&&window.history){
                    window.__fmUrlHook=true;
                    const emit=()=>window.dispatchEvent(new CustomEvent('fmurlchange',{detail:{url:location.href}}));
                    const rawPush=history.pushState;
                    const rawReplace=history.replaceState;
                    history.pushState=function(){const r=rawPush.apply(this,arguments);emit();return r;};
                    history.replaceState=function(){const r=rawReplace.apply(this,arguments);emit();return r;};
                    window.addEventListener('popstate',emit);
                  }
                  %s
                  const player={
                    playUrl:(url,title,options)=>invoke('player.playUrl',Object.assign({},options||{},{url,title})),
                    playVod:(siteKey,vodId,title,pic,options)=>invoke('player.playVod',Object.assign({},options||{},{siteKey,vodId,title,pic})),
                    preloadArtwork:(pic,wallPic)=>invoke('player.preloadArtwork',{pic,wallPic}),
                    control:(action)=>invoke('player.control',{action}),
                    status:()=>invoke('player.status',{})
                  };
                  const net={
                    request:(url,options)=>invoke('net.request',Object.assign({},options||{},{url})),
                    resourceUrl:(url,options)=>fongmiBridge.resourceUrl(url,JSON.stringify(options||{}))
                  };
                  const cache={
                    get:(key,rule)=>invoke('cache.get',{key,rule}),
                    set:(key,value,rule)=>invoke('cache.set',{key,value,rule}),
                    del:(key,rule)=>invoke('cache.del',{key,rule})
                  };
                  const pan={
                    check:(items)=>invoke('pan.check',{items}),
                    play:(payload)=>invoke('pan.play',payload||{})
                  };
                  const ext={
                    info:()=>invoke('ext.info',{}),
                    log:(message,data)=>invoke('ext.log',{message,data}),
                    toast:(message)=>invoke('ext.toast',{message})
                  };
                  const ui={
                    setToolbar:(visible)=>invoke('ui.setToolbar',{visible:visible!==false}),
                    setChrome:(options)=>invoke('ui.setChrome',options||{}),
                    restoreChrome:()=>invoke('ui.restoreChrome',{}),
                    getViewport:()=>invoke('ui.getViewport',{})
                  };
                  window.fongmi={invoke,player,net,cache,
                    app:{
                      search:(keyword,options)=>invoke('app.search',Object.assign({},options||{},{keyword})),
                      openVod:()=>invoke('app.openVod',{}),
                      openLive:()=>invoke('app.openLive',{}),
                      openKeep:()=>invoke('app.openKeep',{}),
                      openSetting:()=>invoke('app.openSetting',{}),
                      history:()=>invoke('app.history',{})
                    },
                    pan,
                    ext,
                    device:{info:()=>invoke('device.info',{})},
                    site:{info:()=>invoke('site.info',{})},
                    config:{info:()=>invoke('config.info',{})},
                    ui,
                    navigation:{
                      back:()=>invoke('navigation.back',{}),
                      reload:()=>invoke('navigation.reload',{})
                    }
                  };
                  window.fm={
                    req:net.request,
                    res:net.resourceUrl,
                    play:player.playUrl,
                    vod:player.playVod,
                    preloadArtwork:player.preloadArtwork,
                    ctrl:player.control,
                    stat:player.status,
                    search:window.fongmi.app.search,
                    openVod:window.fongmi.app.openVod,
                    openLive:window.fongmi.app.openLive,
                    openKeep:window.fongmi.app.openKeep,
                    openSetting:window.fongmi.app.openSetting,
                    history:window.fongmi.app.history,
                    pan,
                    check:window.fongmi.pan.check,
                    cache,
                    ext,
                    ui,
                    device:window.fongmi.device.info,
                    site:window.fongmi.site.info,
                    config:window.fongmi.config.info,
                    back:window.fongmi.navigation.back,
                    reload:window.fongmi.navigation.reload
                  };
                  window.dispatchEvent(new CustomEvent('fmsdk'));
                })();
                """, Util.isLeanback() ? "leanback" : "mobile", Util.isLeanback(), "");
    }

    public void reload() {
        if (isFinishing() || isDestroyed()) return;
        mBinding.webView.loadUrl(reloadUrl(TextUtils.isEmpty(home) ? mBinding.webView.getUrl() : home));
    }

    private String reloadUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            return Uri.parse(url).buildUpon().appendQueryParameter("_fm_reload", String.valueOf(System.currentTimeMillis())).build().toString();
        } catch (Throwable e) {
            return url + (url.contains("?") ? "&" : "?") + "_fm_reload=" + System.currentTimeMillis();
        }
    }

    public void finishNative() {
        finish();
    }

    public String getViewportJson() {
        JsonObject object = new JsonObject();
        object.addProperty("width", mBinding.webView.getWidth());
        object.addProperty("height", mBinding.webView.getHeight());
        object.addProperty("density", getResources().getDisplayMetrics().density);
        return object.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.webView.onResume();
        mBinding.webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        mBinding.webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        active = false;
        mBinding.webView.stopLoading();
        mBinding.webView.destroy();
        super.onDestroy();
    }
}
