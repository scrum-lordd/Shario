package com.localshare;

import android.app.Application;
import com.localshare.persistence.DbHelper;

/* JADX INFO: loaded from: classes11.dex */
public class LocalShareApp extends Application {
    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        new DbHelper(this).getWritableDatabase().close();
    }
}
