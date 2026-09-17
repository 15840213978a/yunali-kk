package com.fongmi.android.tv;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.fongmi.android.tv.event.EventIndex;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        initCrashGuard();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus();
        OkHttp.dns().setDoh(Doh.objectFrom(Setting.getDoh()));
        return null;
    }

    private void initCrashGuard() {
        Thread.UncaughtExceptionHandler delegate = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (isSpiderCrash(thread, throwable)) {
                Log.e("TV", "Spider crash ignored: " + Log.getStackTraceString(throwable));
                return;
            }
            if (delegate != null) delegate.uncaughtException(thread, throwable);
        });
    }

    private boolean isSpiderCrash(Thread thread, Throwable throwable) {
        if (thread == Looper.getMainLooper().getThread()) return false;
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().startsWith("com.github.catvod.spider")) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
