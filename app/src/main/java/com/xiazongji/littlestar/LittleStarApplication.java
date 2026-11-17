package com.xiazongji.littlestar;

import android.app.Application;

public final class LittleStarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
